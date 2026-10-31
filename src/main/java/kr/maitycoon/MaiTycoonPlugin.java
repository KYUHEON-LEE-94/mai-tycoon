package kr.maitycoon;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MaiTycoonPlugin extends JavaPlugin implements TabExecutor {
    private ProfileStore store;
    private Market market;
    private Shop shop;
    private Village village;
    private DexMenu dexMenu;
    private Lands lands;
    private ResourcePack resourcePack;
    private Catalog catalog;
    private CustomCrops customCrops;
    private Lamps lamps;
    private MiniMap miniMap;
    private HelpMenu helpMenu;
    private Tractors tractors;
    private AutoPlanting autoPlanting;
    private TransactionJournal journal;
    private long marketInterval, nextMarketChange;
    private static final List<String> ACTIONS = List.of("help", "info", "starter", "price", "village", "dex", "land", "lamp", "autoplant");
    private static final String CUSTOM_CATEGORY = "특산물";
    private static final int BANNER_SHIFT = 300, OLD_LAND_LIMIT = 9, LAND_LIMIT = 10;
    static final String BUY_PROMPT = "[손 바꾸기]";
    private static final List<String> RETIRED_SEEDS = List.of("STONE_HOE", "IRON_HOE");
    private static final List<String> ADMIN_ACTIONS = List.of("market", "journal", "buildshop", "seedshop", "cropshop", "give");
    @Override public void onEnable() {
        saveDefaultConfig();
        // Merge new sections and crop entries into old installations without replacing configured values.
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig(); // Materialize defaults for getters with explicit fallback values.
        try {
            store = new ProfileStore(getDataFolder().toPath().resolve("players"));
            journal = new TransactionJournal(getDataFolder().toPath().resolve("journal"));
            for (var entry : journal.interrupted()) {
                getLogger().warning("Interrupted trade " + entry.id() + " for " + entry.player() + " at " + entry.createdAt() + ": " + entry.type() + " " + entry.detail());
            }
            retireOldSettings();
            catalog = new Catalog(this, getConfig().getConfigurationSection("custom-crops.crops"));
            market = loadMarket();
            restoreMarket();
            Map<String, String> crops = loadCrops();
            Map<String, Shop.Listing> seeds = loadSeeds();
            marketInterval = getConfig().getInt("market.interval-minutes") * 60_000L;
            if (marketInterval < 60_000) throw new IOException("Invalid market interval");
            shop = new Shop(this, market, store, catalog, crops, seeds, loadShelf("building-shop"), loadShelf("furniture-shop"));
            dexMenu = new DexMenu(this, loadDex(), store, catalog);
            Map<String, Consumer<Player>> npcActions = new HashMap<>();
            int reserve = getConfig().getInt("village.reserve");
            if (reserve < 0) throw new IOException("Invalid village reserve");
            village = new Village(this, npcActions, reserve, getConfig().getBoolean("village.protect-reserve"));
            long landPrice = getConfig().getLong("land.price"), landStep = getConfig().getLong("land.step");
            int landMax = getConfig().getInt("land.max");
            if (landPrice < 1 || landStep < 0 || landMax < 1) throw new IOException("Invalid land settings");
            long reportDays = getConfig().getLong("land.report-days");
            if (reportDays < 1) throw new IOException("Invalid land settings");
            lands = new Lands(this, store, village, landPrice, landStep, landMax, reportDays * 86_400_000L);
            npcActions.put("market", player -> shop.open(player, Shop.Kind.SELL));
            npcActions.put("guide", player -> helpMenu.open(player));
            npcActions.put("building", player -> shop.open(player, Shop.Kind.BUILD));
            npcActions.put("furniture", player -> shop.open(player, Shop.Kind.FURNITURE));
            npcActions.put("seeds", player -> shop.open(player, Shop.Kind.BUY));
            npcActions.put("dex", dexMenu::open);
            npcActions.put("land", lands::open);
            Objects.requireNonNull(getCommand("tycoon")).setExecutor(this);
            Objects.requireNonNull(getCommand("도움말")).setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player) helpMenu.open(player);
                else sender.sendMessage("게임 안에서 사용하세요.");
                return true;
            });
            Objects.requireNonNull(getCommand("tycoon")).setTabCompleter(this);
            getServer().getPluginManager().registerEvents(shop, this);
            getServer().getPluginManager().registerEvents(village, this);
            getServer().getPluginManager().registerEvents(dexMenu, this);
            getServer().getPluginManager().registerEvents(lands, this);
            int cropLimit = getConfig().getInt("custom-crops.chunk-limit");
            if (cropLimit < 1) throw new IOException("Invalid custom crop limit");
            customCrops = new CustomCrops(this, catalog, village, lands, cropLimit, getConfig().getBoolean("custom-crops.bone-meal"));
            getServer().getPluginManager().registerEvents(customCrops, this);
            village.onShowcase(customCrops::plantShowcase);
            lamps = new Lamps(this, store, loadLamp());
            getServer().getPluginManager().registerEvents(lamps, this);
            helpMenu = new HelpMenu(this);
            getServer().getPluginManager().registerEvents(helpMenu, this);
            miniMap = new MiniMap(this);
            getServer().getPluginManager().registerEvents(miniMap, this);
            long plantPrice = getConfig().getLong("auto-plant.zcoin-price"), plantUses = getConfig().getLong("auto-plant.uses");
            if (plantPrice < 1 || plantUses < 1) throw new IOException("Invalid auto-plant settings");
            boolean tractorOn = getConfig().getBoolean("tractor.enabled");
            long tractorPrice = getConfig().getLong("tractor.zcoin-price");
            int tractorRadius = getConfig().getInt("tractor.radius"), tractorPerTick = getConfig().getInt("tractor.blocks-per-tick");
            if (tractorPrice < 1 || tractorRadius < 1 || tractorRadius > 16 || tractorPerTick < 1) throw new IOException("Invalid tractor settings");
            autoPlanting = new AutoPlanting(this, store, village, lands, plantPrice, plantUses, tractorOn ? tractorPrice : 0);
            if (tractorOn) {
                tractors = new Tractors(this, store, village, lands, tractorRadius, tractorPerTick);
                getServer().getPluginManager().registerEvents(tractors, this);
            }
            getServer().getPluginManager().registerEvents(autoPlanting, this);
            try {
                resourcePack = new ResourcePack(this, shop, getConfig().getString("pack.host", "127.0.0.1"), getConfig().getInt("pack.port"));
                getServer().getPluginManager().registerEvents(resourcePack, this);
            } catch (IOException e) {
                // The pack is optional: without it every player gets the plain shop screens.
                getLogger().log(Level.WARNING, "Resource pack is not being served", e);
            }
            long now = System.currentTimeMillis();
            if (nextMarketChange <= 0 || nextMarketChange > now + marketInterval) nextMarketChange = now + marketInterval;
            saveMarket();
            getServer().getScheduler().runTaskTimer(this, () -> {
                if (System.currentTimeMillis() >= nextMarketChange) refreshPrices();
            }, 20L, 20L);
            getServer().getScheduler().runTaskTimer(this, () -> village.lookAtPlayers(), 20L, 10L);
            getServer().getScheduler().runTaskTimer(this, () -> lands.refreshBanners(), 40L, 20L);
            getServer().getScheduler().runTaskTimer(this, () -> miniMap.refresh(), 40L, 20L);
            getServer().getScheduler().runTaskTimer(this, () -> dexMenu.checkBiomes(), 60L, 40L);
            getServer().getScheduler().runTask(this, () -> {
                customCrops.start();
                if (village.built()) { getServer().setSpawnRadius(0); village.upgradeStructures(); village.ensureNpcs(); }
                else if (getConfig().getBoolean("village.auto-build")) village.build(() -> {});
                else getLogger().info("No village yet. /tycoon village build confirm flattens the area around the world spawn and builds it.");
            });
        } catch (IOException | IllegalArgumentException e) {
            getLogger().log(Level.SEVERE, "Cannot enable MaiTycoon", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    /** Merged defaults only ever add keys, so an existing config keeps listing items the shop no longer sells. */
    private void retireOldSettings() {
        boolean changed = false;
        for (String id : RETIRED_SEEDS) {
            if (!getConfig().isSet("seeds." + id)) continue;
            getConfig().set("seeds." + id, null);
            changed = true;
        }
        if ("농기구".equals(getConfig().getString("seeds.BONE_MEAL.category"))) {
            getConfig().set("seeds.BONE_MEAL.category", "비료");
            changed = true;
        }
        if (getConfig().getInt("land.max") == OLD_LAND_LIMIT) {
            getConfig().set("land.max", LAND_LIMIT);
            changed = true;
        }
        if (changed) saveConfig();
    }
    @Override public void onDisable() { if (resourcePack != null) resourcePack.stop(); }
    private Market loadMarket() throws IOException {
        var section = getConfig().getConfigurationSection("crops");
        if (section == null) throw new IOException("Missing crops");
        Map<String, Market.Rule> rules = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            Material crop = Material.matchMaterial(id);
            if (crop == null || !crop.isItem() || !crop.name().equals(id)) throw new IOException("Invalid crop " + id);
            rules.put(id, new Market.Rule(section.getLong(id + ".base", -1), section.getInt(id + ".swing", -1)));
        }
        for (Catalog.Crop crop : catalog.crops()) rules.put(crop.id(), new Market.Rule(crop.base(), crop.swing()));
        return new Market(rules, getConfig().getInt("market.min-percent"), getConfig().getInt("market.max-percent"), getConfig().getLong("market.volume-threshold", 256),
            getConfig().getInt("market.pressure-per-batch", 10), getConfig().getInt("market.max-pressure", 60));
    }
    private Map<String, String> loadCrops() {
        var section = Objects.requireNonNull(getConfig().getConfigurationSection("crops"));
        Map<String, String> crops = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) crops.put(id, section.getString(id + ".category", "기타"));
        for (Catalog.Crop crop : catalog.crops()) crops.put(crop.id(), CUSTOM_CATEGORY);
        return crops;
    }
    private LifeLamp loadLamp() throws IOException {
        var section = getConfig().getConfigurationSection("lamp");
        if (section == null) throw new IOException("Missing lamp");
        Map<LifeLamp.Kind, LifeLamp.Range> ranges = new EnumMap<>(LifeLamp.Kind.class);
        for (String kind : section.getKeys(false)) {
            ranges.put(LifeLamp.Kind.valueOf(kind), new LifeLamp.Range(section.getInt(kind + ".min-percent"), section.getInt(kind + ".max-percent"), section.getInt(kind + ".min-amount"), section.getInt(kind + ".max-amount")));
        }
        return new LifeLamp(ranges);
    }
    /** The title for a menu: the shared dark panel for players with the pack, plain text otherwise. */
    /** Boss bar text is centred, so trailing space pushes it toward the left edge. Without the pack it stays centred. */
    Component bannerTitle(Player player, String text) {
        // The keybind component shows each player's own key (F unless rebound), so a fixed key name could be wrong.
        Component title = text.startsWith(BUY_PROMPT)
            ? Component.text("[", NamedTextColor.GOLD).append(Component.keybind("key.swapOffhand", NamedTextColor.GOLD)).append(Component.text("]" + text.substring(BUY_PROMPT.length()), NamedTextColor.GOLD))
            : Component.text(text, NamedTextColor.GOLD);
        return shop.drawn(player) ? title.append(new ShopTitle().spaceRight(BANNER_SHIFT).build()) : title;
    }
    boolean tractorHarvesting() { return tractors != null && tractors.harvesting(); }
    boolean packDrawn(Player player) { return shop.drawn(player); }
    Component menuTitle(Player player, String title) { return shop.drawn(player) ? new ShopTitle().panel(title).build() : Component.text(title); }
    void openLandTool(Player player, boolean lamp) throws IOException {
        if (lamp) lamps.open(player);
        else autoPlanting.open(player);
    }
    void frame(org.bukkit.inventory.Inventory inventory, Player player) {
        if (shop.drawn(player)) inventory.clear();
        else Ui.frame(inventory);
    }
    private Dex loadDex() throws IOException {
        var pageSection = getConfig().getConfigurationSection("dex.pages");
        var rewardSection = getConfig().getConfigurationSection("dex.rewards");
        if (pageSection == null || rewardSection == null) throw new IOException("Missing dex");
        Map<String, List<String>> pages = new LinkedHashMap<>();
        for (String page : pageSection.getKeys(false)) {
            List<String> entries = pageSection.getStringList(page);
            for (String id : entries) if (Material.matchMaterial(id) == null || !Material.valueOf(id).isItem()) throw new IOException("Invalid dex entry " + id);
            pages.put(page, entries);
        }
        if (pages.isEmpty()) throw new IOException("Empty dex");
        List<String> farm = new ArrayList<>(pages.values().iterator().next());
        for (Catalog.Crop crop : catalog.crops()) farm.add(crop.id());
        pages.put(pages.keySet().iterator().next(), farm);
        if (getConfig().getBoolean("dex.all-items")) pages.putAll(DexItems.remaining(pages));
        Map<Integer, Dex.Reward> rewards = new HashMap<>();
        for (String tier : rewardSection.getKeys(false)) {
            rewards.put(Integer.parseInt(tier), new Dex.Reward(rewardSection.getLong(tier + ".coins"), rewardSection.getLong(tier + ".zcoin"), rewardSection.getBoolean(tier + ".land-permit"), rewardSection.getLong(tier + ".lamp")));
        }
        return new Dex(pages, rewards);
    }
    private Map<String, Shop.Listing> loadSeeds() throws IOException {
        var section = getConfig().getConfigurationSection("seeds");
        if (section == null) throw new IOException("Missing seeds");
        Map<String, Shop.Listing> seeds = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            Material item = Material.matchMaterial(id);
            long price = section.getLong(id + ".price", -1);
            if (item == null || !item.isItem() || price < 1 || price > 1_000_000) throw new IOException("Invalid seed price for " + id);
            // Buying must never undercut the best selling price, or 골드 could be minted by trading back and forth.
            if (market.has(id) && price <= market.ceiling(id)) throw new IOException("Seed price must exceed top market price: " + id);
            seeds.put(id, new Shop.Listing(price, section.getString(id + ".category", "기타")));
        }
        for (Catalog.Crop crop : catalog.crops()) {
            if (crop.seedPrice() < 1) throw new IOException("Invalid seed price for " + crop.id());
            seeds.put(crop.seedId(), new Shop.Listing(crop.seedPrice(), CUSTOM_CATEGORY));
        }
        return seeds;
    }
    private Map<String, Shop.Listing> loadShelf(String name) throws IOException {
        var section = getConfig().getConfigurationSection(name);
        if (section == null) throw new IOException("Missing " + name);
        Map<String, Shop.Listing> result = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            Material material = Material.matchMaterial(id);
            long price = section.getLong(id + ".price", -1);
            if (material == null || !material.isItem() || price < 1 || price > 1_000_000)
                throw new IOException("Invalid item in " + name + ": " + id);
            if (market.has(id) && price <= market.ceiling(id)) throw new IOException("Price below market ceiling in " + name + ": " + id);
            result.put(id, new Shop.Listing(price, section.getString(id + ".category", "장식")));
        }
        return result;
    }
    private File marketFile() { return new File(getDataFolder(), "market.yml"); }
    private void restoreMarket() throws IOException {
        if (!marketFile().exists()) return;
        var yaml = new YamlConfiguration();
        try { yaml.load(marketFile()); }
        catch (InvalidConfigurationException e) { throw new IOException("Invalid market.yml", e); }
        nextMarketChange = yaml.getLong("next-change", 0);
        for (String id : market.ids()) {
            if (!yaml.isInt(id + ".percent")) continue;
            market.restore(id, yaml.getInt(id + ".percent"), yaml.getInt(id + ".previous", yaml.getInt(id + ".percent")));
            market.restoreTrail(id, yaml.getIntegerList(id + ".trail"));
            market.restoreSupply(id, yaml.getLong(id + ".sold", 0), yaml.getInt(id + ".pressure", 0));
        }
    }
    private void saveMarket() throws IOException {
        var yaml = new YamlConfiguration();
        for (String id : market.ids()) {
            yaml.set(id + ".percent", market.percent(id));
            yaml.set(id + ".previous", market.previousPercent(id));
            yaml.set(id + ".trail", market.trail(id));
            yaml.set(id + ".sold", market.sold(id));
            yaml.set(id + ".pressure", market.lastPressure(id));
        }
        yaml.set("next-change", nextMarketChange);
        var destination = marketFile().toPath();
        var temp = java.nio.file.Files.createTempFile(destination.getParent(), "market-", ".tmp");
        try {
            java.nio.file.Files.writeString(temp, yaml.saveToString());
            try { java.nio.file.Files.move(temp, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { java.nio.file.Files.move(temp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } finally { java.nio.file.Files.deleteIfExists(temp); }
    }
    void recordMarketSales(Map<String, Long> counts) throws IOException {
        market.recordSales(counts);
        saveMarket();
    }
    long secondsToNextMarket() { return Math.max(0, (nextMarketChange - System.currentTimeMillis()) / 1000); }
    private void refreshPrices() {
        List<Market.Move> moves = market.step(ThreadLocalRandom.current());
        nextMarketChange = System.currentTimeMillis() + marketInterval;
        try { saveMarket(); }
        catch (IOException e) { getLogger().log(Level.SEVERE, "Cannot save market.yml", e); }
        shop.refreshOpen();
        Market.Move up = moves.stream().max(Comparator.comparingDouble(m -> (double) m.change() / market.base(m.id()))).orElseThrow();
        Market.Move down = moves.stream().min(Comparator.comparingDouble(m -> (double) m.change() / market.base(m.id()))).orElseThrow();
        Component news = Component.text("시세 변동! ").append(moveText(up, NamedTextColor.RED)).append(Component.text(" / ")).append(moveText(down, NamedTextColor.BLUE));
        for (Player player : getServer().getOnlinePlayers()) tell(player, news);
    }
    private Component moveText(Market.Move move, NamedTextColor color) {
        return catalog.name(move.id()).color(color)
            .append(Component.text(" " + move.price() + " 골드 (" + (move.change() >= 0 ? "+" : "") + move.change() + ")", color));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (admin(sender, action, args)) return true;
        if (!(sender instanceof Player player)) { sender.sendMessage("게임 안에서 사용하세요."); return true; }
        if (!player.hasPermission("maitycoon.play")) return true;
        try {
            switch (action) {
                case "info" -> {
                    Profile profile = store.load(player.getUniqueId());
                    tell(player, "보유 골드: " + profile.coins() + " · 즈코인: " + profile.zcoin() + "개 · 생활 램프: " + profile.lamps() + "개");
                }
                case "price" -> prices(player);
                case "buildshop", "seedshop", "cropshop" -> {
                    if (!player.hasPermission("maitycoon.admin")) tell(player, "상점은 광장의 상인을 우클릭해 이용해 주세요.");
                    else shop.open(player, action.equals("buildshop") ? Shop.Kind.BUILD : action.equals("seedshop") ? Shop.Kind.BUY : Shop.Kind.SELL);
                }
                case "starter" -> starter(player);
                case "dex" -> dexMenu.open(player);
                case "help" -> helpMenu.open(player);
                case "lamp" -> lamps.open(player);
                case "autoplant" -> autoPlanting.open(player);
                case "land" -> {
                    if (args.length >= 2 && args[1].equalsIgnoreCase("member")) lands.memberCommand(player, args);
                    else if (args.length >= 2 && args[1].equalsIgnoreCase("buy")) lands.openBuy(player);
                    else lands.open(player);
                }
                case "village" -> {
                    if (!village.built()) tell(player, "마을이 아직 지어지지 않았습니다.");
                    else { player.teleport(village.spawn()); tell(player, "마을 광장으로 이동했습니다."); }
                }
                default -> {
                    tell(player, "광장의 작물 상인에게 작물을 팔고, 씨앗 상인에게 씨앗과 도구를 살 수 있습니다.");
                    if (player.hasPermission("maitycoon.admin")) tell(player, "/tycoon buildshop | seedshop | cropshop — 관리자 상점 열기");
                    tell(player, "/tycoon starter — 시작 보급품 받기 (1회)");
                    tell(player, "/tycoon price — 현재 작물 시세 보기");
                    player.sendMessage(Component.text("[마이 타이쿤] ", NamedTextColor.GREEN).append(Component.keybind("key.swapOffhand", NamedTextColor.YELLOW)).append(Component.text(" 키를 누르면 도감이 열립니다. 구매할 수 있는 땅에서는 구매 화면이 열립니다. 손 바꾸기는 웅크린 채 누르세요.", NamedTextColor.WHITE)));
                    tell(player, "/tycoon dex — 수집 도감 (아이템을 제출해 등록, 30개 달성 시 땅 구매 이용권)");
                    tell(player, "/tycoon land — 땅 지도, 청크 구매, 농장 관리");
                    tell(player, "/tycoon lamp — 손에 든 괭이에 생활 램프 적용");
                    tell(player, "/tycoon autoplant — 자동 심기 설정");
                    tell(player, "/tycoon land member add|remove <이름> — 내 땅의 작업자 관리");
                    tell(player, "/tycoon village — 마을 광장으로 이동");
                    tell(player, "/tycoon info — 보유 골드와 즈코인 확인");
                }
            }
        } catch (IOException | ArithmeticException e) {
            getLogger().log(Level.SEVERE, "Player transaction failed: " + player.getUniqueId(), e);
            tell(player, "데이터를 처리하지 못했습니다. 관리자에게 문의하세요.");
        }
        return true;
    }
    private boolean admin(CommandSender sender, String action, String[] args) {
        boolean build = action.equals("village") && args.length == 2 && args[1].equalsIgnoreCase("build");
        boolean buildNow = action.equals("village") && args.length == 3 && args[1].equalsIgnoreCase("build") && args[2].equalsIgnoreCase("confirm");
        boolean step = action.equals("market") && args.length == 2 && args[1].equalsIgnoreCase("next");
        boolean records = action.equals("journal");
        boolean giveLamp = (action.equals("lamp") || action.equals("autoplant")) && args.length == 4 && args[1].equalsIgnoreCase("give");
        boolean giveMoney = action.equals("give") && args.length == 4;
        if (!build && !buildNow && !step && !records && !giveLamp && !giveMoney) return false;
        if (!sender.hasPermission("maitycoon.admin")) { sender.sendMessage("권한이 없습니다."); return true; }
        if (step) { refreshPrices(); sender.sendMessage("시세를 갱신했습니다."); }
        else if (giveLamp) give(sender, args[2], args[3], action.equals("lamp"));
        else if (giveMoney) giveMoney(sender, args[1], args[2], args[3]);
        else if (build) sender.sendMessage("월드 스폰 주변 약 110칸 사방의 지형을 평탄화하고 마을을 짓습니다. 되돌릴 수 없으니 월드를 백업한 뒤 /tycoon village build confirm 을 입력하세요.");
        else if (buildNow) {
            sender.sendMessage("마을을 짓습니다.");
            village.build(() -> sender.sendMessage("마을 작업이 끝났습니다."));
        }
        else journal(sender, args);
        return true;
    }
    /** /tycoon give <이름> gold|zcoin <수량> */
    private void giveMoney(CommandSender sender, String name, String kind, String amount) {
        var target = getServer().getOfflinePlayerIfCached(name);
        if (target == null) { sender.sendMessage("서버에 접속한 적이 있는 플레이어만 지정할 수 있습니다."); return; }
        boolean gold = kind.equalsIgnoreCase("gold");
        if (!gold && !kind.equalsIgnoreCase("zcoin")) { sender.sendMessage("/tycoon give <이름> gold|zcoin <수량>"); return; }
        try {
            long count = Long.parseLong(amount);
            if (count < 1) throw new NumberFormatException();
            Profile profile = store.load(target.getUniqueId());
            store.save(target.getUniqueId(), gold ? profile.sell(count) : profile.addZcoin(count));
            sender.sendMessage(name + "에게 " + (gold ? count + " 골드" : "즈코인 " + count + "개") + "를 지급했습니다.");
        } catch (NumberFormatException e) { sender.sendMessage("수량을 확인해 주세요."); }
        catch (IOException e) {
            getLogger().log(Level.SEVERE, "Cannot give money", e);
            sender.sendMessage("지급하지 못했습니다.");
        }
    }
    void openDex(Player player) { dexMenu.open(player); }
    private void give(CommandSender sender, String name, String amount, boolean lamp) {
        var target = getServer().getOfflinePlayerIfCached(name);
        if (target == null) { sender.sendMessage("서버에 접속한 적이 있는 플레이어만 지정할 수 있습니다."); return; }
        try {
            long count = Long.parseLong(amount);
            if (count < 1) throw new NumberFormatException();
            Profile profile = store.load(target.getUniqueId());
            store.save(target.getUniqueId(), lamp ? profile.addLamps(count) : profile.addReplants(count));
            sender.sendMessage(name + "에게 " + (lamp ? "생활 램프 " + count + "개" : "자동 심기 " + count + "회") + "를 지급했습니다.");
        } catch (NumberFormatException e) { sender.sendMessage("개수를 확인해 주세요."); }
        catch (IOException e) {
            getLogger().log(Level.SEVERE, "Cannot give lamp", e);
            sender.sendMessage("지급하지 못했습니다.");
        }
    }
    private void journal(CommandSender sender, String[] args) {
        if (args.length == 3 && args[1].equalsIgnoreCase("resolve")) {
            try { sender.sendMessage(journal.resolve(args[2]) ? "거래 기록을 처리했습니다. 해당 플레이어의 거래가 다시 가능합니다." : "그런 기록이 없습니다."); }
            catch (IOException e) {
                getLogger().log(Level.SEVERE, "Cannot resolve journal entry " + args[2], e);
                sender.sendMessage("기록을 처리하지 못했습니다.");
            }
            return;
        }
        if (journal.interrupted().isEmpty()) { sender.sendMessage("중단된 거래가 없습니다."); return; }
        for (var entry : journal.interrupted()) {
            String name = getServer().getOfflinePlayer(entry.player()).getName();
            sender.sendMessage(entry.id() + " · " + (name == null ? entry.player() : name) + " · " + entry.createdAt() + " · " + entry.type() + " " + entry.detail());
        }
        sender.sendMessage("잔액과 아이템을 확인해 바로잡은 뒤 /tycoon journal resolve <번호> 를 입력하세요.");
    }
    boolean tradeHeld(Player player) { return journal.held(player.getUniqueId()); }
    /**
     * Runs a trade under a durable journal entry and saves the inventory before closing it, since the server would
     * otherwise write it much later. The entry survives only if the server dies inside the trade.
     */
    void transact(Player player, String type, String detail, Action action) throws IOException {
        String id = journal.begin(player.getUniqueId(), type, detail);
        action.run();
        player.saveData();
        journal.finish(id);
    }
    private void prices(Player player) {
        tell(player, "현재 시세 (다음 변동까지 약 " + secondsToNextMarket() + "초)");
        for (String id : market.ids()) {
            long change = market.change(id);
            String trend = change > 0 ? " ▲" + change : change < 0 ? " ▼" + -change : "";
            player.sendMessage(Component.text(" · ").append(catalog.name(id)).append(Component.text(" " + market.price(id) + " 골드" + trend)));
        }
    }
    boolean survival(Player player) {
        if (player.getGameMode() == GameMode.SURVIVAL || player.hasPermission("maitycoon.admin")) return true;
        tell(player, "농사 보급과 거래는 서바이벌 모드에서 사용할 수 있습니다.");
        return false;
    }
    private void starter(Player player) throws IOException {
        if (!survival(player)) return;
        Profile profile = store.load(player.getUniqueId());
        if (profile.starterClaimed()) { tell(player, "이미 시작 보급품을 받았습니다."); return; }
        long empty = Arrays.stream(player.getInventory().getStorageContents()).filter(i -> i == null || i.getType().isAir()).count();
        if (empty < 3) { tell(player, "인벤토리에 빈칸 3개가 필요합니다."); return; }
        store.save(player.getUniqueId(), profile.claimStarter());
        player.getInventory().addItem(new ItemStack(Material.STONE_HOE), new ItemStack(Material.WHEAT_SEEDS, 16), new ItemStack(Material.BREAD, 8));
        tell(player, "돌 괭이, 밀 씨앗 16개, 빵 8개를 받았습니다. 마을 공용 농장에서 농사를 시작하세요.");
    }
    interface Action { void run() throws IOException; }
    void guarded(Player player, Action action) {
        try { action.run(); }
        catch (IOException | ArithmeticException | IllegalArgumentException e) {
            getLogger().log(Level.SEVERE, "Player transaction failed: " + player.getUniqueId(), e);
            tell(player, "데이터를 처리하지 못했습니다. 관리자에게 문의하세요.");
        }
    }
    void tell(Player player, String message) { tell(player, Component.text(message, NamedTextColor.WHITE)); }
    void tell(Player player, Component message) {
        player.sendMessage(Component.text("[마이 타이쿤] ", NamedTextColor.GREEN).append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sender.hasPermission("maitycoon.admin")) {
            String next = args[0].equalsIgnoreCase("village") ? "build" : args[0].equalsIgnoreCase("market") ? "next" : args[0].equalsIgnoreCase("journal") ? "resolve" : "";
            return !next.isEmpty() && next.startsWith(typed) ? List.of(next) : List.of();
        }
        if (args.length != 1) return List.of();
        List<String> actions = new ArrayList<>(ACTIONS);
        if (sender.hasPermission("maitycoon.admin")) actions.addAll(ADMIN_ACTIONS);
        return actions.stream().filter(s -> s.startsWith(typed)).toList();
    }
}
