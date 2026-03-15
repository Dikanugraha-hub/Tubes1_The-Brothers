package alternative_bots_2;
import battlecode.common.*;
import java.util.Random;

/**
 * RobotPlayer - Bot Alternative-2 (Resource Specialist)
 * 
 * Fokus: maksimalin ekonomi tim dengan Algoritma Greedy.
 * Strategi Saya - (Perancang: Dzakwan MKPP - 13524145)
 *   1. Greedy SRP  -> cari ruins terdekat, mark pola 5x5, complete resource pattern
 *   2. Greedy Upgrade Tower -> fallback kalau ga ada SRP, upgrade menara tim ke Level 3
 *   3. Paint Management -> kalau cat hampir habis, balik ke menara terdekat buat isi ulang
 */

public class RobotPlayer{
    static int turnCount = 0;
    static int spawnCounter = 0;
    static final Random rng = new Random(6147);
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException{
        rc.setIndicatorString("alternatif2bot hidup! Tipe: " + rc.getType());

        while(true){
            turnCount += 1;

            try{
                switch (rc.getType()){
                    case SOLDIER:
                        runSoldier(rc);
                        break;
                    case MOPPER:
                        runMopper(rc);
                        break;
                    case SPLASHER:
                        runSplasher(rc);
                        break;
                    default:
                        runTower(rc);
                        break;
                }
            } 
            catch(GameActionException e){
                System.out.println("GameActionException: " + e.getMessage());
                e.printStackTrace();
            } 
            catch(Exception e){
                System.out.println("Exception tidak terduga: " + e.getMessage());
                e.printStackTrace();
            } 
            finally{
                Clock.yield();
            }
        }
    }

// TOWER LOGIC.
    public static void runTower(RobotController rc) throws GameActionException{
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation lokSpawn = rc.getLocation().add(dir);
        boolean giliraMopper = (spawnCounter % 4 == 3);

        if(giliraMopper){
            if(rc.canBuildRobot(UnitType.MOPPER, lokSpawn)){
                rc.buildRobot(UnitType.MOPPER, lokSpawn);
                spawnCounter++;
                rc.setIndicatorString("Tower spawn Mopper");
            }
        } 
        else{
            if(rc.canBuildRobot(UnitType.SOLDIER, lokSpawn)){
                rc.buildRobot(UnitType.SOLDIER, lokSpawn);
                spawnCounter++;
                rc.setIndicatorString("Tower spawn Soldier");
            }
        }

        Message[] pesanMasuk = rc.readMessages(-1);
    }

// SOLDIER LOGIC.
    public static void runSoldier(RobotController rc) throws GameActionException{
        MapLocation posisiSekarang = rc.getLocation();
        int catSaatIni = rc.getPaint();
        int catMaksimal = rc.getType().paintCapacity;
        int rondeSekarang = rc.getRoundNum();

        boolean butuhRefill = catSaatIni < (catMaksimal / 5);
        if(butuhRefill){
            rc.setIndicatorString("Cat sisa " + catSaatIni + ", cari menara refill");
            boolean berhasilRefill = cariMenaraUntukRefill(rc);
            if(berhasilRefill){
                return;
            }
        }

        boolean sudahKerjaSRP = false;
        if(rondeSekarang < 1850){
            sudahKerjaSRP = kerjakanSRP(rc);
        }

        if(!sudahKerjaSRP){
            boolean sudahUpgrade = cobaUpgradeTower(rc);

            if(!sudahUpgrade){
                gerakRandom(rc);
            }
        }

        catTileDibawahKaki(rc);
    }

    public static boolean kerjakanSRP(RobotController rc) throws GameActionException{
        MapLocation posisiSekarang = rc.getLocation();

        MapLocation[] semuaRuins = rc.senseNearbyRuins(-1);
        if(semuaRuins.length == 0){
            return false;
        }

        MapLocation targetRuin = null;
        int jarakTerdekat = Integer.MAX_VALUE;

        for(int i = 0; i < semuaRuins.length; i++){
            if(Clock.getBytecodesLeft() < 1000) break;
            
            MapLocation lokasiRuin = semuaRuins[i];
            MapInfo infoRuin = rc.senseMapInfo(lokasiRuin);
            if(infoRuin.isResourcePatternCenter()){
                continue;
            }

            if(rc.canSenseRobotAtLocation(lokasiRuin)){
                RobotInfo robotDisitu = rc.senseRobotAtLocation(lokasiRuin);
                if(robotDisitu != null && robotDisitu.getType().isTowerType()){
                    continue;
                }
            }

            // Hitung jarak (greedy: pilih yang paling deket)
            int jarak = posisiSekarang.distanceSquaredTo(lokasiRuin);
            if(jarak < jarakTerdekat){
                jarakTerdekat = jarak;
                targetRuin = lokasiRuin;
            }
        }
        if(targetRuin == null){
            return false;
        }

        rc.setIndicatorString("Target SRP di ruins: " + targetRuin);
        Direction arahKeTarget = posisiSekarang.directionTo(targetRuin);
        if(rc.canMove(arahKeTarget)){
            rc.move(arahKeTarget);
            posisiSekarang = rc.getLocation();
        }
        if(rc.canMarkResourcePattern(targetRuin)){
            rc.markResourcePattern(targetRuin);
        }

        boolean[][] polaSRP = rc.getResourcePattern();
        warnaiPolaSRP(rc, targetRuin, polaSRP);
        if(rc.getChips() >= 200 && rc.canCompleteResourcePattern(targetRuin)){
            rc.completeResourcePattern(targetRuin);
            rc.setIndicatorString("SRP COMPLETE!");
            rc.setTimelineMarker("SRP Complete", 0, 200, 0);
        }
        return true;
    }

    public static void warnaiPolaSRP(RobotController rc, MapLocation center, boolean[][] polaSRP) throws GameActionException{
        if(!rc.isActionReady()){
            return;
        }

        // Loop manual 5x5 di sekitar center
        for(int baris = 0; baris < 5; baris++){
            for(int kolom = 0; kolom < 5; kolom++){
                // BYTECODE SHIELD
                if(Clock.getBytecodesLeft() < 1000) return;

                int offsetX = kolom - 2;
                int offsetY = baris - 2;
                MapLocation lokTile = center.translate(offsetX, offsetY);
                if(!rc.canSenseLocation(lokTile)){
                    continue;
                }

                MapInfo infoTile = rc.senseMapInfo(lokTile);
                PaintType catSekarangDiTile = infoTile.getPaint();
                boolean pakaiSecondary = polaSRP[baris][kolom];

                if(pakaiSecondary && catSekarangDiTile == PaintType.ALLY_SECONDARY){
                    continue;
                }
                if(!pakaiSecondary && catSekarangDiTile == PaintType.ALLY_PRIMARY){
                    continue;
                }

                if(rc.canAttack(lokTile)){
                    rc.attack(lokTile, pakaiSecondary);
                    return;
                }
            }
        }
    }


// GREEDY UPGRADE TOWER.
    public static boolean cobaUpgradeTower(RobotController rc) throws GameActionException{
        MapLocation posisiSekarang = rc.getLocation();
        Team timKita = rc.getTeam();
        RobotInfo[] robotSekitar = rc.senseNearbyRobots(-1, timKita);
        MapLocation targetMenara = null;
        int hpTertinggi = -1;
        int jarakKeMenara = Integer.MAX_VALUE;

        for(int i = 0; i < robotSekitar.length; i++){
            // BYTECODE SHIELD
            if(Clock.getBytecodesLeft() < 1000) break;

            RobotInfo info = robotSekitar[i];
            UnitType tipeMenara = info.getType();

            if(!tipeMenara.isTowerType()){
                continue;
            }
            if(!tipeMenara.canUpgradeType()){
                continue;
            }

            int hpMenara = info.getHealth();
            int jarakMenara = posisiSekarang.distanceSquaredTo(info.getLocation());

            if(hpMenara > hpTertinggi || (hpMenara == hpTertinggi && jarakMenara < jarakKeMenara)){
                hpTertinggi = hpMenara;
                jarakKeMenara = jarakMenara;
                targetMenara = info.getLocation();
            }
        }
        if(targetMenara == null){
            return false;
        }

        rc.setIndicatorString("OTW Upgrade menara di " + targetMenara);
        Direction arahKeMenara = posisiSekarang.directionTo(targetMenara);
        if(rc.canMove(arahKeMenara)){
            rc.move(arahKeMenara);
        }

        if(rc.canUpgradeTower(targetMenara)){
            rc.upgradeTower(targetMenara);
            rc.setTimelineMarker("Tower Upgraded", 255, 200, 0);
            return true;
        }
        return true;
    }

// PAINT MANAGEMENT.
    public static boolean cariMenaraUntukRefill(RobotController rc) throws GameActionException{
        MapLocation posisiSekarang = rc.getLocation();
        Team timKita = rc.getTeam();

        // Cari menara tim terdekat
        RobotInfo[] robotSekitar = rc.senseNearbyRobots(-1, timKita);
        MapLocation menaraTerdekat = null;
        int jarakMin = Integer.MAX_VALUE;

        for(int i = 0; i < robotSekitar.length; i++){
            // BYTECODE SHIELD
            if(Clock.getBytecodesLeft() < 1000) break;
            
            RobotInfo info = robotSekitar[i];
            if(!info.getType().isTowerType()){
                continue;
            }

            int jarak = posisiSekarang.distanceSquaredTo(info.getLocation());
            if(jarak < jarakMin){
                jarakMin = jarak;
                menaraTerdekat = info.getLocation();
            }
        }
        if(menaraTerdekat == null){
            gerakRandom(rc);
            return false;
        }

        rc.setIndicatorString("Refill OTW -> " + menaraTerdekat);
        Direction arahKeMenara = posisiSekarang.directionTo(menaraTerdekat);
        if(rc.canMove(arahKeMenara)){
            rc.move(arahKeMenara);
        }

        int catYangDiminta = rc.getType().paintCapacity - rc.getPaint();
        if(rc.canTransferPaint(menaraTerdekat, -catYangDiminta)){
            rc.transferPaint(menaraTerdekat, -catYangDiminta);
            rc.setIndicatorString("Refill sukses!");
        }
        return true;
    }

// HELPER.
    public static void gerakRandom(RobotController rc) throws GameActionException{
        int mulaiDari = rng.nextInt(directions.length);
        for(int i = 0; i < directions.length; i++){
            if(Clock.getBytecodesLeft() < 500) break; // Bytecode Shield

            int idx = (mulaiDari + i) % directions.length;
            Direction dir = directions[idx];
            if(rc.canMove(dir)){
                rc.move(dir);
                break;
            }
        }
    }

    public static void catTileDibawahKaki(RobotController rc) throws GameActionException{
        MapLocation posisi = rc.getLocation();
        if(!rc.canSenseLocation(posisi)){
            return;
        }
        MapInfo tileDibawah = rc.senseMapInfo(posisi);
        if(!tileDibawah.getPaint().isAlly() && rc.canAttack(posisi)){
            rc.attack(posisi);
        }
    }

// MOPPER LOGIC {bukan fokus utama}.
    public static void runMopper(RobotController rc) throws GameActionException{
        Direction dir = directions[rng.nextInt(directions.length)];
        if(rc.canMove(dir)){
            rc.move(dir);
        }
        MapLocation depan = rc.getLocation().add(dir);
        if(rc.canMopSwing(dir)){
            rc.mopSwing(dir);
        } 
        else if(rc.canAttack(depan)){
            rc.attack(depan);
        }
    }

// SPLASHER LOGIC {bukan fokus utama}.
    public static void runSplasher(RobotController rc) throws GameActionException{
        gerakRandom(rc);
    }
}
