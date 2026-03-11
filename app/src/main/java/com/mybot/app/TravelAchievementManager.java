package com.mybot.app;

import android.content.Context;
import android.widget.Toast;

import java.util.List;

public class TravelAchievementManager {

    // Achievement IDs
    public static final String ACH_FIRST_TRIP = "first_trip";
    public static final String ACH_BUDGET_MASTER = "budget_master";
    public static final String ACH_THREE_TRIPS = "three_trips";
    public static final String ACH_TEN_TRIPS = "ten_trips";
    public static final String ACH_REGION_NORTH = "region_north";
    public static final String ACH_REGION_HSINCHU = "region_hsinchu";
    public static final String ACH_REGION_CENTRAL = "region_central";
    public static final String ACH_REGION_YUNLIN = "region_yunlin";
    public static final String ACH_REGION_SOUTH = "region_south";
    public static final String ACH_REGION_EAST = "region_east";
    public static final String ACH_REGION_ISLAND = "region_island";
    public static final String ACH_ALL_REGIONS = "all_regions";
    public static final String ACH_LONG_TRIP = "long_trip";
    public static final String ACH_SOLO = "solo_traveler";
    public static final String ACH_GROUP = "group_trip";
    public static final String ACH_FOODIE = "foodie";
    public static final String ACH_NATURE = "nature_lover";

    public static final int TOTAL_ACHIEVEMENTS = 17;

    // Region mapping
    private static final String[] ALL_REGIONS = {
        "北北基", "桃竹苗", "中彰投", "雲嘉南", "高屏", "宜花東", "離島"
    };

    public static String getRegionForDestination(String destination) {
        if (destination == null) return "";
        if (destination.contains("台北") || destination.contains("新北") || destination.contains("基隆"))
            return "北北基";
        if (destination.contains("桃園") || destination.contains("新竹") || destination.contains("苗栗"))
            return "桃竹苗";
        if (destination.contains("台中") || destination.contains("彰化") || destination.contains("南投"))
            return "中彰投";
        if (destination.contains("雲林") || destination.contains("嘉義") || destination.contains("台南"))
            return "雲嘉南";
        if (destination.contains("高雄") || destination.contains("屏東"))
            return "高屏";
        if (destination.contains("宜蘭") || destination.contains("花蓮") || destination.contains("台東"))
            return "宜花東";
        if (destination.contains("澎湖") || destination.contains("金門") || destination.contains("馬祖")
                || destination.contains("蘭嶼") || destination.contains("綠島") || destination.contains("小琉球"))
            return "離島";
        return "";
    }

    public static void checkAndUnlock(Context context, long tripId) {
        TravelDbHelper db = TravelDbHelper.getInstance(context);

        android.database.Cursor c = db.getTripById(tripId);
        if (!c.moveToFirst()) { c.close(); return; }

        String destination = c.getString(c.getColumnIndexOrThrow(TravelDbHelper.COL_DESTINATION));
        int days = c.getInt(c.getColumnIndexOrThrow(TravelDbHelper.COL_DAYS));
        int people = c.getInt(c.getColumnIndexOrThrow(TravelDbHelper.COL_PEOPLE));
        String preferences = c.getString(c.getColumnIndexOrThrow(TravelDbHelper.COL_PREFERENCES));
        double estimated = c.getDouble(c.getColumnIndexOrThrow(TravelDbHelper.COL_ESTIMATED_BUDGET));
        double actual = c.getDouble(c.getColumnIndexOrThrow(TravelDbHelper.COL_ACTUAL_BUDGET));
        c.close();

        int completedCount = db.getCompletedTripCount();
        String region = getRegionForDestination(destination);

        // Record stats
        if (!region.isEmpty()) {
            db.insertStat(tripId, region, 0, actual > 0 ? actual : estimated);
        }

        // First trip
        tryUnlock(context, db, ACH_FIRST_TRIP, completedCount >= 1);

        // Three trips
        tryUnlock(context, db, ACH_THREE_TRIPS, completedCount >= 3);
        db.updateAchievementProgress(ACH_THREE_TRIPS, Math.min(completedCount, 3));

        // Ten trips
        tryUnlock(context, db, ACH_TEN_TRIPS, completedCount >= 10);
        db.updateAchievementProgress(ACH_TEN_TRIPS, Math.min(completedCount, 10));

        // Budget master
        if (estimated > 0 && actual > 0 && actual <= estimated) {
            tryUnlock(context, db, ACH_BUDGET_MASTER, true);
        }

        // Long trip (5+ days)
        if (days >= 5) {
            tryUnlock(context, db, ACH_LONG_TRIP, true);
        }

        // Solo traveler
        if (people == 1) {
            tryUnlock(context, db, ACH_SOLO, true);
        }

        // Group trip (5+ people)
        if (people >= 5) {
            tryUnlock(context, db, ACH_GROUP, true);
        }

        // Region achievements
        if ("北北基".equals(region)) tryUnlock(context, db, ACH_REGION_NORTH, true);
        if ("桃竹苗".equals(region)) tryUnlock(context, db, ACH_REGION_HSINCHU, true);
        if ("中彰投".equals(region)) tryUnlock(context, db, ACH_REGION_CENTRAL, true);
        if ("雲嘉南".equals(region)) tryUnlock(context, db, ACH_REGION_YUNLIN, true);
        if ("高屏".equals(region)) tryUnlock(context, db, ACH_REGION_SOUTH, true);
        if ("宜花東".equals(region)) tryUnlock(context, db, ACH_REGION_EAST, true);
        if ("離島".equals(region)) tryUnlock(context, db, ACH_REGION_ISLAND, true);

        // All regions
        List<String> visited = db.getVisitedRegions();
        if (visited.size() >= ALL_REGIONS.length) {
            tryUnlock(context, db, ACH_ALL_REGIONS, true);
        }
        db.updateAchievementProgress(ACH_ALL_REGIONS, visited.size());

        // Preference-based achievements
        if (preferences != null) {
            if (preferences.contains("美食")) {
                int foodieCount = db.getCompletedTripCountByPreference("美食");
                db.updateAchievementProgress(ACH_FOODIE, Math.min(foodieCount, 3));
                if (foodieCount >= 3) tryUnlock(context, db, ACH_FOODIE, true);
            }
            if (preferences.contains("自然")) {
                int natureCount = db.getCompletedTripCountByPreference("自然");
                db.updateAchievementProgress(ACH_NATURE, Math.min(natureCount, 3));
                if (natureCount >= 3) tryUnlock(context, db, ACH_NATURE, true);
            }
        }

        AppLog.i("Travel", "成就檢查完成 tripId=" + tripId + " completed=" + completedCount + " region=" + region);
    }

    private static void tryUnlock(Context context, TravelDbHelper db, String achId, boolean condition) {
        if (condition && !db.isAchievementUnlocked(achId)) {
            db.unlockAchievement(achId);
            String name = getAchievementName(achId);
            String icon = getAchievementIcon(achId);
            Toast.makeText(context, icon + " 成就解鎖：" + name, Toast.LENGTH_LONG).show();
            AppLog.i("Travel", "成就解鎖: " + achId + " (" + name + ")");
        }
    }

    public static String getAchievementName(String achId) {
        switch (achId) {
            case ACH_FIRST_TRIP: return "初心旅人";
            case ACH_BUDGET_MASTER: return "精打細算";
            case ACH_THREE_TRIPS: return "旅遊達人";
            case ACH_TEN_TRIPS: return "環島勇者";
            case ACH_REGION_NORTH: return "北部探索者";
            case ACH_REGION_HSINCHU: return "竹苗探索者";
            case ACH_REGION_CENTRAL: return "中部探索者";
            case ACH_REGION_YUNLIN: return "雲嘉南探索者";
            case ACH_REGION_SOUTH: return "南部探索者";
            case ACH_REGION_EAST: return "東部探索者";
            case ACH_REGION_ISLAND: return "離島冒險家";
            case ACH_ALL_REGIONS: return "寶島制霸";
            case ACH_LONG_TRIP: return "深度旅行家";
            case ACH_SOLO: return "獨行俠";
            case ACH_GROUP: return "團康高手";
            case ACH_FOODIE: return "美食獵人";
            case ACH_NATURE: return "自然愛好者";
            default: return achId;
        }
    }

    public static String getAchievementIcon(String achId) {
        switch (achId) {
            case ACH_FIRST_TRIP: return "\uD83C\uDF92";    // 🎒
            case ACH_BUDGET_MASTER: return "\uD83D\uDCB0"; // 💰
            case ACH_THREE_TRIPS: return "\u2B50";          // ⭐
            case ACH_TEN_TRIPS: return "\uD83C\uDFC6";     // 🏆
            case ACH_REGION_NORTH:
            case ACH_REGION_HSINCHU:
            case ACH_REGION_CENTRAL:
            case ACH_REGION_YUNLIN:
            case ACH_REGION_SOUTH:
            case ACH_REGION_EAST: return "\uD83D\uDDFA";   // 🗺️
            case ACH_REGION_ISLAND: return "\uD83C\uDFDD"; // 🏝️
            case ACH_ALL_REGIONS: return "\uD83D\uDC51";   // 👑
            case ACH_LONG_TRIP: return "\uD83C\uDFD5";     // 🏕️
            case ACH_SOLO: return "\uD83E\uDDED";          // 🧭
            case ACH_GROUP: return "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"; // 👨‍👩‍👧‍👦
            case ACH_FOODIE: return "\uD83C\uDF5C";        // 🍜
            case ACH_NATURE: return "\uD83C\uDF3F";        // 🌿
            default: return "\uD83C\uDFC5";                // 🏅
        }
    }

    public static String getAchievementDescription(String achId) {
        switch (achId) {
            case ACH_FIRST_TRIP: return "完成第一次旅行";
            case ACH_BUDGET_MASTER: return "完成一趟低於預算的旅行";
            case ACH_THREE_TRIPS: return "完成 3 次旅行";
            case ACH_TEN_TRIPS: return "完成 10 次旅行";
            case ACH_REGION_NORTH: return "造訪台北/新北/基隆";
            case ACH_REGION_HSINCHU: return "造訪桃園/新竹/苗栗";
            case ACH_REGION_CENTRAL: return "造訪台中/彰化/南投";
            case ACH_REGION_YUNLIN: return "造訪雲林/嘉義/台南";
            case ACH_REGION_SOUTH: return "造訪高雄/屏東";
            case ACH_REGION_EAST: return "造訪宜蘭/花蓮/台東";
            case ACH_REGION_ISLAND: return "造訪離島";
            case ACH_ALL_REGIONS: return "造訪台灣所有 7 大區域";
            case ACH_LONG_TRIP: return "完成 5 天以上的旅行";
            case ACH_SOLO: return "完成一次獨自旅行";
            case ACH_GROUP: return "完成一次 5 人以上團體旅行";
            case ACH_FOODIE: return "完成 3 次美食主題旅行";
            case ACH_NATURE: return "完成 3 次自然主題旅行";
            default: return "";
        }
    }

    public static int getAchievementMaxProgress(String achId) {
        switch (achId) {
            case ACH_THREE_TRIPS: return 3;
            case ACH_TEN_TRIPS: return 10;
            case ACH_ALL_REGIONS: return 7;
            case ACH_FOODIE: return 3;
            case ACH_NATURE: return 3;
            default: return 1;
        }
    }

    public static String[] getAllAchievementIds() {
        return new String[]{
            ACH_FIRST_TRIP, ACH_BUDGET_MASTER, ACH_THREE_TRIPS, ACH_TEN_TRIPS,
            ACH_REGION_NORTH, ACH_REGION_HSINCHU, ACH_REGION_CENTRAL, ACH_REGION_YUNLIN,
            ACH_REGION_SOUTH, ACH_REGION_EAST, ACH_REGION_ISLAND, ACH_ALL_REGIONS,
            ACH_LONG_TRIP, ACH_SOLO, ACH_GROUP, ACH_FOODIE, ACH_NATURE
        };
    }
}
