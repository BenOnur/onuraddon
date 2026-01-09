package com.example.addon.hud;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

public class DonutPriceTrackerHud extends HudElement {
    public static final HudElementInfo<DonutPriceTrackerHud> INFO = new HudElementInfo<>(
        Hud.GROUP,
        "donut-price-tracker",
        "Tracks item prices from DonutSMP Auction House and Orders",
        DonutPriceTrackerHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Items");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General settings
    private final Setting<String> apiKey = sgGeneral.add(new StringSetting.Builder()
        .name("api-key")
        .description("Your DonutSMP API key")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("How often to update prices (in seconds)")
        .defaultValue(30)
        .min(10)
        .sliderMax(300)
        .build()
    );

    private final Setting<Boolean> showAH = sgGeneral.add(new BoolSetting.Builder()
        .name("show-auction-house")
        .description("Show Auction House prices")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showOrder = sgGeneral.add(new BoolSetting.Builder()
        .name("show-orders")
        .description("Show Order prices")
        .defaultValue(true)
        .build()
    );

    // Item tracking settings
    private final Setting<List<String>> trackedItems = sgItems.add(new StringListSetting.Builder()
        .name("tracked-items")
        .description("Items to track (use Minecraft item IDs, e.g., minecraft:diamond)")
        .defaultValue(Arrays.asList(
            "minecraft:diamond",
            "minecraft:netherite_ingot",
            "minecraft:emerald"
        ))
        .build()
    );

    // Color settings
    private final Setting<Color> titleColor = sgColors.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color of the title text")
        .defaultValue(new Color(255, 255, 255))
        .build()
    );

    private final Setting<Color> itemColor = sgColors.add(new ColorSetting.Builder()
        .name("item-color")
        .description("Color of item names")
        .defaultValue(new Color(255, 215, 0))
        .build()
    );

    private final Setting<Color> ahColor = sgColors.add(new ColorSetting.Builder()
        .name("ah-price-color")
        .description("Color of AH prices")
        .defaultValue(new Color(50, 205, 50))
        .build()
    );

    private final Setting<Color> orderColor = sgColors.add(new ColorSetting.Builder()
        .name("order-price-color")
        .description("Color of Order prices")
        .defaultValue(new Color(255, 69, 0))
        .build()
    );

    // Data storage
    private final Map<String, PriceData> priceCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private long lastUpdate = 0;

    public DonutPriceTrackerHud() {
        super(INFO);
        
        // Schedule periodic updates
        scheduler.scheduleAtFixedRate(this::updatePrices, 0, updateInterval.get(), TimeUnit.SECONDS);
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(renderer.textWidth("DonutSMP Price Tracker") + 10, 20);

        double x = this.x;
        double y = this.y;

        // Title
        renderer.text("DonutSMP Price Tracker", x, y, titleColor.get());
        y += renderer.textHeight() + 2;

        // Check if we have items to track
        if (trackedItems.get().isEmpty()) {
            renderer.text("No items tracked", x, y, Color.GRAY);
            return;
        }

        // Display each tracked item
        for (String itemId : trackedItems.get()) {
            PriceData data = priceCache.get(itemId);
            
            // Get item name
            String itemName = getItemName(itemId);
            renderer.text(itemName + ":", x, y, itemColor.get());
            y += renderer.textHeight() + 1;

            if (data != null && !data.isError) {
                // Show AH price
                if (showAH.get() && data.ahPrice > 0) {
                    String ahText = "  AH: $" + formatPrice(data.ahPrice);
                    renderer.text(ahText, x, y, ahColor.get());
                    y += renderer.textHeight() + 1;
                }

                // Show Order price
                if (showOrder.get() && data.orderPrice > 0) {
                    String orderText = "  Order: $" + formatPrice(data.orderPrice);
                    renderer.text(orderText, x, y, orderColor.get());
                    y += renderer.textHeight() + 1;
                }

                // Calculate and show profit margin
                if (showAH.get() && showOrder.get() && data.ahPrice > 0 && data.orderPrice > 0) {
                    double margin = data.orderPrice - data.ahPrice;
                    double marginPercent = (margin / data.ahPrice) * 100;
                    String marginText = String.format("  Profit: $%s (%.1f%%)", 
                        formatPrice(margin), marginPercent);
                    Color marginColor = margin > 0 ? Color.GREEN : Color.RED;
                    renderer.text(marginText, x, y, marginColor);
                    y += renderer.textHeight() + 1;
                }
            } else if (data != null && data.isError) {
                renderer.text("  Error loading", x, y, Color.RED);
                y += renderer.textHeight() + 1;
            } else {
                renderer.text("  Loading...", x, y, Color.GRAY);
                y += renderer.textHeight() + 1;
            }

            y += 2; // Space between items
        }

        // Update time
        long secondsSinceUpdate = (System.currentTimeMillis() - lastUpdate) / 1000;
        String updateText = "Updated " + secondsSinceUpdate + "s ago";
        renderer.text(updateText, x, y, Color.GRAY);

        // Update size
        setSize(renderer.textWidth("DonutSMP Price Tracker") + 10, y - this.y + renderer.textHeight());
    }

    private void updatePrices() {
        if (apiKey.get().isEmpty()) {
            return;
        }

        for (String itemId : trackedItems.get()) {
            CompletableFuture.runAsync(() -> {
                try {
                    PriceData data = new PriceData();
                    
                    // Fetch AH price (cheapest)
                    if (showAH.get()) {
                        data.ahPrice = fetchAHPrice(itemId);
                    }
                    
                    // Fetch Order price (highest)
                    if (showOrder.get()) {
                        data.orderPrice = fetchOrderPrice(itemId);
                    }
                    
                    priceCache.put(itemId, data);
                } catch (Exception e) {
                    PriceData errorData = new PriceData();
                    errorData.isError = true;
                    priceCache.put(itemId, errorData);
                    e.printStackTrace();
                }
            });
        }

        lastUpdate = System.currentTimeMillis();
    }

    private double fetchAHPrice(String itemId) throws Exception {
        // API endpoint for auction house - you may need to adjust this URL
        String urlString = "https://api.donutsmp.net/auction?item=" + itemId + "&sort=price&order=asc&limit=1";
        String response = makeAPIRequest(urlString);
        
        // Parse JSON response
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        
        // Adjust these field names based on actual API response structure
        if (json.has("data") && json.getAsJsonArray("data").size() > 0) {
            JsonObject firstItem = json.getAsJsonArray("data").get(0).getAsJsonObject();
            return firstItem.get("price").getAsDouble();
        }
        
        return 0;
    }

    private double fetchOrderPrice(String itemId) throws Exception {
        // API endpoint for orders - you may need to adjust this URL
        String urlString = "https://api.donutsmp.net/order?item=" + itemId + "&sort=price&order=desc&limit=1";
        String response = makeAPIRequest(urlString);
        
        // Parse JSON response
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        
        // Adjust these field names based on actual API response structure
        if (json.has("data") && json.getAsJsonArray("data").size() > 0) {
            JsonObject firstItem = json.getAsJsonArray("data").get(0).getAsJsonObject();
            return firstItem.get("price").getAsDouble();
        }
        
        return 0;
    }

    private String makeAPIRequest(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey.get());
        conn.setRequestProperty("Accept", "application/json");
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("API request failed with code: " + responseCode);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }

    private String getItemName(String itemId) {
        try {
            Identifier id = new Identifier(itemId);
            Item item = Registries.ITEM.get(id);
            if (item != Items.AIR) {
                return item.getName().getString();
            }
        } catch (Exception e) {
            // If parsing fails, return the ID itself
        }
        return itemId.replace("minecraft:", "");
    }

    private String formatPrice(double price) {
        if (price >= 1_000_000) {
            return String.format("%.2fM", price / 1_000_000);
        } else if (price >= 1_000) {
            return String.format("%.2fK", price / 1_000);
        } else {
            return String.format("%.2f", price);
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        scheduler.shutdown();
    }

    private static class PriceData {
        double ahPrice = 0;
        double orderPrice = 0;
        boolean isError = false;
    }
}
