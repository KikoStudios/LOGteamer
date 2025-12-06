package studio.overload.logteamer.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import studio.overload.logteamer.Logteamer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class IconCache {
    private static final IconCache INSTANCE = new IconCache();
    private final Map<String, ResourceLocation> icons = new HashMap<>();
    private final Set<String> pending = new HashSet<>();
    private final Set<String> failed = new HashSet<>();

    public static IconCache getInstance() {
        return INSTANCE;
    }

    public ResourceLocation getIcon(String url, boolean isMask) {
        if (url == null || url.isEmpty())
            return null;
        String key = url + (isMask ? "_mask" : "");
        if (failed.contains(key))
            return null;
        if (icons.containsKey(key))
            return icons.get(key);

        if (!pending.contains(key)) {
            pending.add(key);
            CompletableFuture.runAsync(() -> downloadIcon(url, isMask));
        }

        return null;
    }

    private void downloadIcon(String urlString, boolean isMask) {
        String key = urlString + (isMask ? "_mask" : "");
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.connect();

            if (connection.getResponseCode() / 100 != 2) {
                failed.add(key);
                return;
            }

            try (InputStream is = connection.getInputStream()) {
                NativeImage image = NativeImage.read(is);

                if (isMask) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        for (int y = 0; y < image.getHeight(); y++) {
                            int color = image.getPixelRGBA(x, y);
                            int alpha = (color >> 24) & 0xFF;
                            if (alpha > 10) { // Threshold for "visible"
                                // Set to White (255, 255, 255) + Alpha
                                // NativeImage RGBA format: AABBGGRR (Little Endian?) or RRGB?
                                // getPixelRGBA returns int. format depends on platform?
                                // Actually valid setPixelRGBA takes int.
                                // Let's just set 0xFFFFFFFF if we want white?
                                // Wait, alpha needs to be preserved.
                                // 0xAABBGGRR
                                int white = (alpha << 24) | 0x00FFFFFF;
                                image.setPixelRGBA(x, y, white);
                            }
                        }
                    }
                }

                // Schedule registration on main thread
                Minecraft.getInstance().execute(() -> {
                    try {
                        DynamicTexture texture = new DynamicTexture(image);
                        ResourceLocation location = new ResourceLocation(Logteamer.MODID,
                                "icon_" + Math.abs(key.hashCode()));
                        Minecraft.getInstance().getTextureManager().register(location, texture);
                        icons.put(key, location);
                    } catch (Exception e) {
                        failed.add(key);
                    }
                });
            }
        } catch (Exception e) {
            failed.add(key);
        } finally {
            pending.remove(key);
        }
    }
}
