package dev.marblegate.superpipeslide.client.core.projection.cache;

import com.mojang.blaze3d.platform.NativeImage;
import dev.marblegate.superpipeslide.common.SuperPipeSlide;
import dev.marblegate.superpipeslide.config.ClientConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class ProjectionNetworkImageCache {
    private static final int MAX_QUEUE_SIZE = 32;
    private static final long FAILURE_COOLDOWN_MILLIS = 300_000L;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_SCRIPT_CHALLENGES = 2;
    private static final String NETWORK_IMAGE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) SuperPipeSlide/1.0 Safari/537.36";
    private static final String IMAGE_ACCEPT = "image/png,image/jpeg,image/*;q=0.8,*/*;q=0.5";
    private static final Pattern DOCUMENT_COOKIE_PATTERN = Pattern.compile("document\\.cookie\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION_HREF_PATTERN = Pattern.compile("(?:window\\.)?location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Set<String> LOGGED_NOT_IMAGE_RESPONSES = ConcurrentHashMap.newKeySet();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "SuperPipeSlide Projection Image Loader");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>(32, 0.75F, true);
    private static final Queue<CompletedLoad> COMPLETED = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED_LOADS = new AtomicInteger();

    private ProjectionNetworkImageCache() {
    }

    public static State state(String url) {
        Validation validation = validate(url);
        if (!validation.ok()) {
            return new State(validation.state(), null, 0, 0, validation.messageKey());
        }
        Entry entry;
        synchronized (ENTRIES) {
            entry = ENTRIES.get(validation.normalizedUrl());
            if (entry == null) {
                entry = new Entry(validation.normalizedUrl());
                ENTRIES.put(validation.normalizedUrl(), entry);
            }
            entry.lastAccessMillis = System.currentTimeMillis();
            maybeStartLoad(entry);
            trimLocked();
            return entry.state();
        }
    }

    public static void reload(String url) {
        Validation validation = validate(url);
        if (!validation.ok()) {
            return;
        }
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.remove(validation.normalizedUrl());
            if (entry != null) {
                release(entry);
            }
            Entry next = new Entry(validation.normalizedUrl());
            ENTRIES.put(validation.normalizedUrl(), next);
            maybeStartLoad(next);
        }
    }

    public static void clearUrl(String url) {
        Validation validation = validate(url);
        if (!validation.ok()) {
            return;
        }
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.remove(validation.normalizedUrl());
            if (entry != null) {
                release(entry);
            }
        }
    }

    public static void tick() {
        pumpCompleted();
    }

    public static void clear() {
        CompletedLoad completed;
        while ((completed = COMPLETED.poll()) != null) {
            if (completed.image() != null) {
                completed.image().close();
            }
        }
        synchronized (ENTRIES) {
            for (Entry entry : ENTRIES.values()) {
                release(entry);
            }
            ENTRIES.clear();
        }
        LOGGED_NOT_IMAGE_RESPONSES.clear();
        QUEUED_LOADS.set(0);
    }

    private static Validation validate(String url) {
        if (!ClientConfig.ENABLE_PROJECTION_NETWORK_IMAGES.get()) {
            return Validation.blocked(Status.DISABLED, "screen.superpipeslide.projection_image.disabled");
        }
        String raw = url == null ? "" : url.trim();
        if (raw.isBlank()) {
            return Validation.blocked(Status.EMPTY, "screen.superpipeslide.projection_image.empty");
        }
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException ignored) {
            return Validation.blocked(Status.INVALID_URL, "screen.superpipeslide.projection_image.invalid_url");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && ClientConfig.ALLOW_HTTP_PROJECTION_NETWORK_IMAGES.get())) {
            return Validation.blocked(Status.BLOCKED, "screen.superpipeslide.projection_image.blocked_scheme");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return Validation.blocked(Status.INVALID_URL, "screen.superpipeslide.projection_image.invalid_url");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            return Validation.blocked(Status.BLOCKED, "screen.superpipeslide.projection_image.blocked_host");
        }
        if (blockedHostLiteral(uri.getHost())) {
            return Validation.blocked(Status.BLOCKED, "screen.superpipeslide.projection_image.blocked_host");
        }
        URI normalized;
        try {
            normalized = new URI(scheme, null, uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(), uri.getPath(), uri.getQuery(), null).normalize();
        } catch (URISyntaxException ignored) {
            return Validation.blocked(Status.INVALID_URL, "screen.superpipeslide.projection_image.invalid_url");
        }
        return new Validation(true, normalized.toString(), Status.QUEUED, "");
    }

    private static boolean blockedHostLiteral(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
            return true;
        }
        if (isIpv4Literal(normalized)) {
            return blockedAddress(ipv4Address(normalized));
        }
        if (normalized.indexOf(':') >= 0) {
            try {
                return blockedAddress(InetAddress.getByName(normalized));
            } catch (UnknownHostException ignored) {
                return true;
            }
        }
        return false;
    }

    private static boolean blockedResolvedHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || blockedHostLiteral(normalized)) {
            return true;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalized);
            for (InetAddress address : addresses) {
                if (blockedAddress(address)) {
                    return true;
                }
            }
        } catch (UnknownHostException ignored) {
            return true;
        }
        return false;
    }

    private static boolean blockedAddress(InetAddress address) {
        return address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    private static boolean isIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isBlank() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                return false;
            }
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private static InetAddress ipv4Address(String host) {
        String[] parts = host.split("\\.");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i]);
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static void maybeStartLoad(Entry entry) {
        long now = System.currentTimeMillis();
        if (entry.status == Status.READY || entry.status == Status.QUEUED || entry.status == Status.DOWNLOADING || entry.status == Status.DECODING) {
            return;
        }
        if (entry.failedAtMillis > 0L && now - entry.failedAtMillis < FAILURE_COOLDOWN_MILLIS) {
            return;
        }
        if (QUEUED_LOADS.get() >= MAX_QUEUE_SIZE) {
            entry.status = Status.FAILED;
            entry.messageKey = "screen.superpipeslide.projection_image.queue_full";
            entry.failedAtMillis = now;
            return;
        }
        entry.status = Status.QUEUED;
        entry.messageKey = "screen.superpipeslide.projection_image.loading";
        QUEUED_LOADS.incrementAndGet();
        EXECUTOR.execute(() -> load(entry.url));
    }

    private static void load(String url) {
        try {
            markStatus(url, Status.DOWNLOADING, "screen.superpipeslide.projection_image.loading");
            COMPLETED.add(download(url, url, 0, 0, ""));
        } catch (Exception exception) {
            COMPLETED.add(CompletedLoad.failure(url, Status.FAILED, "screen.superpipeslide.projection_image.failed"));
            SuperPipeSlide.LOGGER.debug("Projection image failed to load {}", url, exception);
        } finally {
            QUEUED_LOADS.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    private static CompletedLoad download(String entryUrl, String url, int redirects, int scriptChallenges, String cookieHeader) throws IOException, InterruptedException {
        if (redirects > MAX_REDIRECTS) {
            return CompletedLoad.failure(entryUrl, Status.BLOCKED, "screen.superpipeslide.projection_image.redirects");
        }
        URI uri = URI.create(url);
        if (blockedResolvedHost(uri.getHost())) {
            return CompletedLoad.failure(entryUrl, Status.BLOCKED, "screen.superpipeslide.projection_image.blocked_host");
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", NETWORK_IMAGE_USER_AGENT)
                .header("Accept", IMAGE_ACCEPT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                .header("Cache-Control", "no-cache")
                .GET();
        if (cookieHeader != null && !cookieHeader.isBlank()) {
            requestBuilder.header("Cookie", cookieHeader);
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        String responseCookieHeader = mergeSetCookies(cookieHeader, response.headers().allValues("set-cookie"));
        if (status >= 300 && status < 400) {
            closeQuietly(response.body());
            Optional<String> location = response.headers().firstValue("location");
            if (location.isEmpty()) {
                return CompletedLoad.failure(entryUrl, Status.FAILED, "screen.superpipeslide.projection_image.failed");
            }
            URI redirected = URI.create(url).resolve(location.get());
            Validation validation = validate(redirected.toString());
            if (!validation.ok()) {
                return CompletedLoad.failure(entryUrl, validation.state(), validation.messageKey());
            }
            return download(entryUrl, validation.normalizedUrl(), redirects + 1, scriptChallenges, responseCookieHeader);
        }
        if (status < 200 || status >= 300) {
            closeQuietly(response.body());
            return CompletedLoad.failure(entryUrl, Status.FAILED, "screen.superpipeslide.projection_image.failed");
        }
        byte[] bytes;
        try (InputStream body = response.body()) {
            bytes = readLimited(body, ClientConfig.PROJECTION_NETWORK_IMAGE_MAX_BYTES.get());
        }
        if (bytes == null || bytes.length == 0) {
            return CompletedLoad.failure(entryUrl, Status.FAILED, "screen.superpipeslide.projection_image.failed");
        }
        if (bytes.length > ClientConfig.PROJECTION_NETWORK_IMAGE_MAX_BYTES.get()) {
            return CompletedLoad.failure(entryUrl, Status.TOO_LARGE, "screen.superpipeslide.projection_image.too_large");
        }
        markStatus(entryUrl, Status.DECODING, "screen.superpipeslide.projection_image.loading");
        String contentType = response.headers().firstValue("content-type").orElse("");
        ImageFormat format = detectFormat(bytes);
        if (format == ImageFormat.WEBP) {
            return CompletedLoad.failure(entryUrl, Status.UNSUPPORTED_FORMAT, "screen.superpipeslide.projection_image.webp_unsupported");
        }
        if (format == ImageFormat.UNKNOWN && looksLikeTextResponse(contentType, bytes)) {
            Optional<ScriptImageChallenge> challenge = scriptImageChallenge(bytes, uri);
            if (challenge.isPresent() && scriptChallenges < MAX_SCRIPT_CHALLENGES) {
                Validation validation = validate(challenge.get().url());
                if (!validation.ok()) {
                    return CompletedLoad.failure(entryUrl, validation.state(), validation.messageKey());
                }
                return download(entryUrl, validation.normalizedUrl(), redirects, scriptChallenges + 1, mergeCookies(responseCookieHeader, challenge.get().cookie()));
            }
            logNotImageOnce(entryUrl, contentType, bytes);
            return CompletedLoad.failure(entryUrl, Status.NOT_IMAGE, "screen.superpipeslide.projection_image.not_image");
        }
        NativeImage image;
        try {
            image = decode(bytes);
        } catch (Throwable throwable) {
            ImageFormat imageIoFormat = imageIoFormat(bytes);
            if (imageIoFormat == ImageFormat.WEBP) {
                return CompletedLoad.failure(entryUrl, Status.UNSUPPORTED_FORMAT, "screen.superpipeslide.projection_image.webp_unsupported");
            }
            ImageFormat effectiveFormat = format == ImageFormat.UNKNOWN ? imageIoFormat : format;
            SuperPipeSlide.LOGGER.debug("Projection image decode failed for {} (contentType={}, detected={}, imageIo={}, firstBytes={})", entryUrl, contentType, format, imageIoFormat, firstBytesHex(bytes), throwable);
            if (effectiveFormat == ImageFormat.UNKNOWN && !looksLikeImageContentType(contentType)) {
                return CompletedLoad.failure(entryUrl, Status.UNSUPPORTED_FORMAT, "screen.superpipeslide.projection_image.unsupported");
            }
            return CompletedLoad.failure(entryUrl, Status.DECODE_FAILED, "screen.superpipeslide.projection_image.decode_failed");
        }
        long pixels = image.getWidth() * (long) image.getHeight();
        if (pixels <= 0 || pixels > ClientConfig.PROJECTION_NETWORK_IMAGE_MAX_PIXELS.get()) {
            image.close();
            return CompletedLoad.failure(entryUrl, Status.TOO_LARGE, "screen.superpipeslide.projection_image.too_large");
        }
        return CompletedLoad.success(entryUrl, image);
    }

    private static Optional<ScriptImageChallenge> scriptImageChallenge(byte[] bytes, URI currentUri) {
        if (bytes == null || bytes.length == 0 || currentUri == null) {
            return Optional.empty();
        }
        String text = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.UTF_8);
        Matcher cookieMatcher = DOCUMENT_COOKIE_PATTERN.matcher(text);
        Matcher locationMatcher = LOCATION_HREF_PATTERN.matcher(text);
        if (!cookieMatcher.find() || !locationMatcher.find()) {
            return Optional.empty();
        }
        String cookie = cookiePair(cookieMatcher.group(1));
        if (cookie.isBlank()) {
            return Optional.empty();
        }
        URI target = currentUri.resolve(locationMatcher.group(1));
        if (!sameHost(currentUri, target)) {
            return Optional.empty();
        }
        return Optional.of(new ScriptImageChallenge(target.toString(), cookie));
    }

    private static boolean sameHost(URI first, URI second) {
        if (first == null || second == null || first.getHost() == null || second.getHost() == null) {
            return false;
        }
        return first.getHost().equalsIgnoreCase(second.getHost())
                && Objects.equals(first.getScheme(), second.getScheme())
                && first.getPort() == second.getPort();
    }

    private static String cookiePair(String raw) {
        String cookie = raw == null ? "" : raw.trim();
        if (cookie.isBlank()) {
            return "";
        }
        int semicolon = cookie.indexOf(';');
        if (semicolon >= 0) {
            cookie = cookie.substring(0, semicolon).trim();
        }
        int equals = cookie.indexOf('=');
        if (equals <= 0 || equals == cookie.length() - 1) {
            return "";
        }
        return cookie;
    }

    private static String mergeCookies(String existing, String next) {
        String pair = cookiePair(next);
        if (pair.isBlank()) {
            return existing == null ? "" : existing;
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        if (existing != null && !existing.isBlank()) {
            for (String item : existing.split(";")) {
                String existingPair = cookiePair(item);
                int equals = existingPair.indexOf('=');
                if (equals > 0) {
                    cookies.put(existingPair.substring(0, equals), existingPair.substring(equals + 1));
                }
            }
        }
        int equals = pair.indexOf('=');
        cookies.put(pair.substring(0, equals), pair.substring(equals + 1));
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String mergeSetCookies(String existing, List<String> setCookies) {
        String merged = existing == null ? "" : existing;
        if (setCookies == null || setCookies.isEmpty()) {
            return merged;
        }
        for (String setCookie : setCookies) {
            merged = mergeCookies(merged, setCookie);
        }
        return merged;
    }

    private static void logNotImageOnce(String entryUrl, String contentType, byte[] bytes) {
        String firstBytes = firstBytesHex(bytes);
        String key = entryUrl + "|" + contentType + "|" + firstBytes;
        if (LOGGED_NOT_IMAGE_RESPONSES.add(key)) {
            SuperPipeSlide.LOGGER.debug("Projection image URL did not return image data: url={}, contentType={}, firstBytes={}", entryUrl, contentType, firstBytes);
        }
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        int limit = Math.max(1, maxBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            if (total + read > limit) {
                return new byte[limit + 1];
            }
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private static NativeImage decode(byte[] bytes) throws IOException {
        ImageFormat detectedFormat = detectFormat(bytes);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            return NativeImage.read(input);
        } catch (Throwable ignored) {
            ImageFormat imageIoFormat = imageIoFormat(bytes);
            if (imageIoFormat == ImageFormat.WEBP) {
                throw new IOException("WebP is not supported");
            }
            ImageFormat effectiveFormat = detectedFormat == ImageFormat.UNKNOWN ? imageIoFormat : detectedFormat;
            if (effectiveFormat != ImageFormat.PNG && effectiveFormat != ImageFormat.JPEG) {
                throw new IOException("Unsupported ImageIO format " + imageIoFormat);
            }
            BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
            if (buffered == null) {
                throw new IOException("No ImageIO reader accepted image");
            }
            return toNativeImage(buffered);
        }
    }

    private static NativeImage toNativeImage(BufferedImage buffered) {
        int width = Math.max(1, buffered.getWidth());
        int height = Math.max(1, buffered.getHeight());
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, buffered.getRGB(x, y));
            }
        }
        return image;
    }

    private static ImageFormat detectFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return ImageFormat.UNKNOWN;
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return ImageFormat.PNG;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return ImageFormat.JPEG;
        }
        if (bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50) {
            return ImageFormat.WEBP;
        }
        return ImageFormat.UNKNOWN;
    }

    private static boolean looksLikeImageContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("image/png")
                || normalized.contains("image/jpeg")
                || normalized.contains("image/jpg");
    }

    private static ImageFormat imageIoFormat(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return ImageFormat.UNKNOWN;
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return ImageFormat.UNKNOWN;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return ImageFormat.UNKNOWN;
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (format == null) {
                    return ImageFormat.UNKNOWN;
                }
                String normalized = format.trim().toLowerCase(Locale.ROOT);
                if (normalized.equals("png")) {
                    return ImageFormat.PNG;
                }
                if (normalized.equals("jpeg") || normalized.equals("jpg")) {
                    return ImageFormat.JPEG;
                }
                if (normalized.equals("webp")) {
                    return ImageFormat.WEBP;
                }
                return ImageFormat.UNKNOWN;
            } finally {
                reader.dispose();
            }
        } catch (IOException ignored) {
            return ImageFormat.UNKNOWN;
        }
    }

    private static boolean looksLikeTextResponse(String contentType, byte[] bytes) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("text/")
                || normalized.contains("json")
                || normalized.contains("xml")
                || normalized.contains("html")
                || normalized.contains("javascript")) {
            return true;
        }
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        int index = 0;
        while (index < bytes.length && index < 16 && Character.isWhitespace((char) (bytes[index] & 0xFF))) {
            index++;
        }
        if (index >= bytes.length) {
            return false;
        }
        int first = bytes[index] & 0xFF;
        return first == '<' || first == '{' || first == '[';
    }

    private static String firstBytesHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int count = Math.min(16, bytes.length);
        StringBuilder builder = new StringBuilder(count * 3);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        return builder.toString();
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    private static void markStatus(String url, Status status, String messageKey) {
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(url);
            if (entry != null && entry.status != Status.READY) {
                entry.status = status;
                entry.messageKey = messageKey;
            }
        }
    }

    private static void pumpCompleted() {
        CompletedLoad completed;
        int processed = 0;
        while (processed++ < 8 && (completed = COMPLETED.poll()) != null) {
            applyCompleted(completed);
        }
    }

    private static void applyCompleted(CompletedLoad completed) {
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(completed.url());
            if (entry == null) {
                if (completed.image() != null) {
                    completed.image().close();
                }
                return;
            }
            if (completed.image() == null) {
                entry.status = completed.status();
                entry.messageKey = completed.messageKey();
                entry.failedAtMillis = System.currentTimeMillis();
                return;
            }
            release(entry);
            Identifier textureId = Identifier.fromNamespaceAndPath(SuperPipeSlide.MODID, "dynamic/projection_image/" + sha1(completed.url()));
            DynamicTexture texture = new DynamicTexture(() -> "SuperPipeSlide projection image " + completed.url(), completed.image());
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            entry.textureId = textureId;
            entry.texture = texture;
            entry.width = completed.image().getWidth();
            entry.height = completed.image().getHeight();
            entry.status = Status.READY;
            entry.messageKey = "";
            entry.failedAtMillis = 0L;
            trimLocked();
        }
    }

    private static void trimLocked() {
        int max = ClientConfig.PROJECTION_NETWORK_IMAGE_CACHE_SIZE.get();
        Iterator<Map.Entry<String, Entry>> iterator = ENTRIES.entrySet().iterator();
        while (ENTRIES.size() > max && iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.status == Status.QUEUED || entry.status == Status.DOWNLOADING || entry.status == Status.DECODING) {
                continue;
            }
            release(entry);
            iterator.remove();
        }
    }

    private static void release(Entry entry) {
        if (entry.textureId != null) {
            Minecraft.getInstance().getTextureManager().release(entry.textureId);
        }
        entry.textureId = null;
        entry.texture = null;
        entry.width = 0;
        entry.height = 0;
    }

    private static String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 16 && i < bytes.length; i++) {
                builder.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xFF));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return Integer.toUnsignedString(input.hashCode(), 16);
        }
    }

    public enum Status {
        EMPTY,
        DISABLED,
        INVALID_URL,
        BLOCKED,
        QUEUED,
        DOWNLOADING,
        DECODING,
        READY,
        FAILED,
        TOO_LARGE,
        UNSUPPORTED_FORMAT,
        NOT_IMAGE,
        DECODE_FAILED
    }

    public record State(Status status, Identifier textureId, int width, int height, String messageKey) {
        public boolean ready() {
            return this.status == Status.READY && this.textureId != null && this.width > 0 && this.height > 0;
        }
    }

    private static final class Entry {
        private final String url;
        private Status status = Status.FAILED;
        private Identifier textureId;
        private DynamicTexture texture;
        private int width;
        private int height;
        private String messageKey = "screen.superpipeslide.projection_image.loading";
        private long failedAtMillis;
        private long lastAccessMillis = System.currentTimeMillis();

        private Entry(String url) {
            this.url = url;
        }

        private State state() {
            return new State(this.status, this.textureId, this.width, this.height, this.messageKey);
        }
    }

    private record Validation(boolean ok, String normalizedUrl, Status state, String messageKey) {
        private static Validation blocked(Status state, String messageKey) {
            return new Validation(false, "", state, messageKey);
        }
    }

    private record CompletedLoad(String url, Status status, String messageKey, NativeImage image) {
        private static CompletedLoad success(String url, NativeImage image) {
            return new CompletedLoad(url, Status.READY, "", image);
        }

        private static CompletedLoad failure(String url, Status status, String messageKey) {
            return new CompletedLoad(url, status, messageKey, null);
        }
    }

    private record ScriptImageChallenge(String url, String cookie) {
    }

    private enum ImageFormat {
        PNG,
        JPEG,
        WEBP,
        UNKNOWN
    }
}
