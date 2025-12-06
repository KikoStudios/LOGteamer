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

    public ResourceLocation getIcon(String url) {
        if (url == null || url.isEmpty())
            return null;
        if (failed.contains(url))
            return null;
        if (icons.containsKey(url))
            return icons.get(url);

        if (!pending.contains(url)) {
            pending.add(url);
            CompletableFuture.runAsync(() -> downloadIcon(url));
        }

        return null; // Return null while loading
    }

    private void downloadIcon(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.connect();

            if (connection.getResponseCode() / 100 != 2) {
                failed.add(urlString);
                return;
            }

            try (InputStream is = connection.getInputStream()) {
                NativeImage image = NativeImage.read(is);

                // Schedule registration on main thread
                Minecraft.getInstance().execute(() -> {
                    try {
                        DynamicTexture texture = new DynamicTexture(image);
                        ResourceLocation location = new ResourceLocation(Logteamer.MODID,
                                "icon_" + Math.abs(urlString.hashCode()));
                        Minecraft.getInstance().getTextureManager().register(location, texture);
                        icons.put(urlString, location);
                    } catch (Exception e) {
                        failed.add(urlString);
                        // Log error
                    }
                });
            }
        } catch (Exception e) {
            failed.add(urlString);
        } finally {
            pending.remove(urlString);
        }
    }
}
