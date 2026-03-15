package main_bot;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class RobotPlayer {
    static final int SOLDIER_REFILL_THRESHOLD = 50;
    static final int SPLASHER_REFILL_THRESHOLD = 80;
    static final int MOPPER_REFILL_THRESHOLD = 25;
    static final int ATTACK_RANGE_SQ = 9;
    static final int SPLASHER_ATTACK_RANGE_SQ = 4;

    static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };

    static final Direction[] cardinals = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST,
    };

    static MapLocation homeTower = null;
    static MapLocation targetRuin = null;
    static UnitType targetTowerType = null;
    static MapLocation exploreTarget = null;

    static MapLocation[] pastLocations = new MapLocation[4];
    static int pastLocIdx = 0;
    static int stuckCount = 0;
    static int myZone = -1;

    public static void run(RobotController rc) throws GameActionException {
        if (homeTower == null) {
            for (RobotInfo r : rc.senseNearbyRobots(-1, rc.getTeam())) {
                if (r.getType().isTowerType()) {
                    homeTower = r.getLocation();
                    break;
                }
            }
        }

        if (myZone == -1) {
            myZone = rc.getID() % 8;
        }

        while (true) {
            try {
                switch (rc.getType()) {
                    case SOLDIER -> runSoldier(rc);
                    case MOPPER -> runMopper(rc);
                    case SPLASHER -> runSplasher(rc);
                    default -> runTower(rc);
                }
            } catch (GameActionException e) {
                System.out.println("GAE: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("ERR: " + e.getMessage());
            } finally {
                Clock.yield();
            }
        }
    }

    // Logika Tower
    public static void runTower(RobotController rc) throws GameActionException {
        attackNearestEnemy(rc);
        broadcastEnemyInfo(rc);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int nS = 0, nSp = 0, nM = 0;
        for (RobotInfo r : allies) {
            if (r.getType() == UnitType.SOLDIER) nS++;
            if (r.getType() == UnitType.SPLASHER) nSp++;
            if (r.getType() == UnitType.MOPPER) nM++;
            if (Clock.getBytecodesLeft() < 1500) break; 
        }

        int total = nS + nSp + nM;
        UnitType toBuild;
        if (total == 0) {
            toBuild = UnitType.SOLDIER;
        } else {
            double dS  = 0.60 - (double) nS  / total;
            double dSp = 0.30 - (double) nSp / total;
            double dM  = 0.10 - (double) nM  / total;
            if (dS >= dSp && dS >= dM) toBuild = UnitType.SOLDIER;
            else if (dSp >= dM) toBuild = UnitType.SPLASHER;
            else toBuild = UnitType.MOPPER;
        }

        boolean built = tryBuildRobot(rc, toBuild);
        if (!built && toBuild != UnitType.SOLDIER)
            tryBuildRobot(rc, UnitType.SOLDIER);
    }

    static boolean tryBuildRobot(RobotController rc, UnitType type) throws GameActionException {
        int startIndex = rc.getID() % 8;
        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(startIndex + i) % 8];
            MapLocation loc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(type, loc)) {
                rc.buildRobot(type, loc);
                return true;
            }
        }
        return false;
    }

    static void attackNearestEnemy(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (enemies.length == 0) return;
        RobotInfo weakest = enemies[0];

        for (RobotInfo e : enemies) {
            if (e.getHealth() < weakest.getHealth()) weakest = e;
        }

        if (rc.canAttack(weakest.getLocation()))
            rc.attack(weakest.getLocation());

        MapLocation aoe = densestEnemy(enemies);
        if (aoe != null && rc.canAttack(aoe)) rc.attack(aoe);
    }

    static MapLocation densestEnemy(RobotInfo[] en) {
        if (en.length == 0) return null;
        MapLocation best = null; int bestN = 0;
        
        int limitOuter = Math.min(en.length, 15);
        for (int i=0; i < limitOuter; i++) {

            if (Clock.getBytecodesLeft() < 2000) break; 
            RobotInfo a = en[i];
            int n = 0;

            for (int j=0; j < en.length; j++) {
                if (a.getLocation().distanceSquaredTo(en[j].getLocation()) <= 4) n++;
            }

            if (n > bestN) { bestN = n; best = a.getLocation(); }
        }
        return best;
    }

    static void broadcastEnemyInfo(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0 && rc.canBroadcastMessage())
            rc.broadcastMessage(enemies.length);
    }

    // Logika Soldier
    public static void runSoldier(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        updateStuckDetection(rc);

        if (rc.getPaint() < SOLDIER_REFILL_THRESHOLD) {
            if (!tryWithdrawFromNearbyTower(rc))
                moveTowards(rc, findNearestTower(rc));
            return;
        }

        if (targetRuin != null && rc.canSenseLocation(targetRuin)) {
            boolean hasTower = false;
            for (RobotInfo r : rc.senseNearbyRobots(targetRuin, 0, null))
                if (r.getType().isTowerType()) { hasTower = true; break; }
            if (hasTower) {
                targetRuin = null;
                targetTowerType = null;
            }
        }

        if (targetRuin == null) {
            MapInfo bestRuinTile = findBestRuin(rc, nearbyTiles);
            if (bestRuinTile != null) {
                targetRuin = bestRuinTile.getMapLocation();
                targetTowerType = chooseTowerType(rc);
            }
        }

        if (targetRuin != null) {
            if (rc.canSenseLocation(targetRuin)) {
                boolean done = handleRuinClaiming(rc, targetRuin, targetTowerType, nearbyTiles);
                if (!done) return;
            } else {
                if (rc.isActionReady()) {
                    MapLocation bestTarget = findBestAttackTarget(rc, nearbyTiles);
                    if (bestTarget != null && rc.canAttack(bestTarget)) {
                        boolean sec = rc.senseMapInfo(bestTarget).getMark() == PaintType.ALLY_SECONDARY;
                        rc.attack(bestTarget, sec);
                    }
                }
                if (rc.isMovementReady()) {
                    Direction dir = moveTowardsWithAvoidance(rc, targetRuin, nearbyTiles);
                    if (dir != null && rc.canMove(dir)) {
                        rc.move(dir);
                        paintCurrentTile(rc);
                    }
                }
                return;
            }
        }

        if (rc.isActionReady()) {
            MapLocation best = findBestAttackTarget(rc, nearbyTiles);
            if (best != null && rc.canAttack(best)) {
                boolean sec = rc.senseMapInfo(best).getMark() == PaintType.ALLY_SECONDARY;
                rc.attack(best, sec);
            }
        }

        if (rc.isMovementReady()) {
            if (exploreTarget == null
                || rc.getLocation().distanceSquaredTo(exploreTarget) <= 4
                || stuckCount > 3) {
                exploreTarget = computeExploreTarget(rc);
                if (stuckCount > 3) stuckCount = 0; 
            }
            Direction dir = findBestExpansionDirection(rc, nearbyTiles);
            if (dir != null && rc.canMove(dir)) {
                rc.move(dir);
                paintCurrentTile(rc);
            }
        }
        rc.setIndicatorString("zone=" + myZone + " tgt=" + exploreTarget + " r=" + targetRuin);
    }

    // RUIN CLAIMING
    static UnitType chooseTowerType(RobotController rc) {
        return rc.getChips() > 2000
            ? UnitType.LEVEL_ONE_PAINT_TOWER
            : UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    static boolean handleRuinClaiming(RobotController rc, MapLocation ruinLoc, UnitType towerType, MapInfo[] nearbyTiles) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        if (rc.canCompleteTowerPattern(towerType, ruinLoc)) {
            rc.completeTowerPattern(towerType, ruinLoc);
            rc.setTimelineMarker("Tower built!", 0, 200, 0);
            targetRuin = null;
            targetTowerType = null;
            exploreTarget = null;
            return true;
        }

        if (rc.canMarkTowerPattern(towerType, ruinLoc))
            rc.markTowerPattern(towerType, ruinLoc);

        if (rc.isActionReady()) {
            MapInfo bestTile = null;
            int bestDist = Integer.MAX_VALUE;

            for (MapInfo tile : nearbyTiles) {
                if (Clock.getBytecodesLeft() < 1000) break; 
                MapLocation tileLoc = tile.getMapLocation();

                if (tileLoc.distanceSquaredTo(ruinLoc) > 12) continue;
                
                if (!rc.canAttack(tileLoc)) continue;
                
                PaintType mark  = tile.getMark();
                PaintType paint = tile.getPaint();

                if (mark == PaintType.EMPTY) continue;

                if (isCorrectColor(mark, paint)) continue; 
                
                int d = myLoc.distanceSquaredTo(tileLoc);
                
                if (d < bestDist) { bestDist = d; bestTile = tile; }
            }
            if (bestTile != null) {
                boolean sec = (bestTile.getMark() == PaintType.ALLY_SECONDARY);
                rc.attack(bestTile.getMapLocation(), sec);
                
                if (rc.canCompleteTowerPattern(towerType, ruinLoc)) {
                    rc.completeTowerPattern(towerType, ruinLoc);
                    targetRuin = null;
                    targetTowerType = null;
                    exploreTarget = null;
                    return true;
                }
                return false;
            }
        }

        if (rc.isMovementReady() && myLoc.distanceSquaredTo(ruinLoc) > 2) {
            Direction dir = moveTowardsWithAvoidance(rc, ruinLoc, nearbyTiles);
            if (dir != null && rc.canMove(dir)) {
                rc.move(dir);
                if (rc.canCompleteTowerPattern(towerType, ruinLoc)) {
                    rc.completeTowerPattern(towerType, ruinLoc);
                    targetRuin = null;
                    targetTowerType = null;
                    exploreTarget = null;
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isCorrectColor(PaintType mark, PaintType paint) {
        if (mark == PaintType.ALLY_PRIMARY) return paint == PaintType.ALLY_PRIMARY;
        if (mark == PaintType.ALLY_SECONDARY) return paint == PaintType.ALLY_SECONDARY;
        return false;
    }

    static MapInfo findBestRuin(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        MapInfo best = null; 
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (Clock.getBytecodesLeft() < 1000) break; 
            if (!tile.hasRuin()) continue;
            
            RobotInfo[] botsOnRuin = rc.senseNearbyRobots(tile.getMapLocation(), 2, null);
            boolean hasTower = false;
            int allySoldiers = 0;
            for (RobotInfo r : botsOnRuin) {
                if (r.getType().isTowerType()) { hasTower = true; break; }
                if (r.getTeam() == rc.getTeam() && r.getType() == UnitType.SOLDIER) {
                    allySoldiers++;
                }
            }
            
            if (!hasTower && allySoldiers < 2) {
                int d = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (d < bestDist) { bestDist = d; best = tile; }
            }
        }
        return best;
    }

    // SISTEM EKSPLORASI TARGET RELATIF
    static MapLocation computeExploreTarget(RobotController rc) {
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();
        MapLocation baseLoc = homeTower != null ? homeTower : rc.getLocation();
        
        // Pilih Arah menggunakan Zona
        Direction primaryDir = directions[myZone];

        // Bergerak menjauhi base setengah peta
        int dist = Math.max(w, h) / 2; 
        
        MapLocation primaryZone = baseLoc.translate(primaryDir.dx * dist, primaryDir.dy * dist);

        if (stuckCount > 3) {
            Direction alterDir = directions[(myZone + 2) % 8];
            return baseLoc.translate(alterDir.dx * dist, alterDir.dy * dist);
        }

        return primaryZone;
    }

    static Direction findBestExpansionDirection(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();
        
        int startIndex = rc.getID() % 8;

        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(startIndex + i) % 8];
            if (!rc.canMove(dir)) continue;
            
            MapLocation nextLoc = myLoc.add(dir);
            int score = 0;
            
            if (Clock.getBytecodesLeft() > 3000) {
                for (MapInfo tile : nearbyTiles) {
                    if (nextLoc.distanceSquaredTo(tile.getMapLocation()) <= ATTACK_RANGE_SQ) {
                        PaintType p = tile.getPaint();
                        if (p == PaintType.EMPTY) score += 4;
                        else if (p.isEnemy()) score += 2;
                    }
                }
            }
            
            if (exploreTarget != null) {
                int curDist  = myLoc.distanceSquaredTo(exploreTarget);
                int nextDist = nextLoc.distanceSquaredTo(exploreTarget);
                if (nextDist < curDist) score += 15;
                else if (nextDist > curDist) score -= 5;
            }

            if (targetRuin != null) {
                int curDist  = myLoc.distanceSquaredTo(targetRuin);
                int nextDist = nextLoc.distanceSquaredTo(targetRuin);
                if (nextDist < curDist) score += 10;
            }

            if (rc.senseMapInfo(nextLoc).getPaint().isAlly()) score -= 2;
            
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        
        if (bestDir == null) {
            for (int i = 0; i < 8; i++) {
                Direction d = directions[(startIndex + i) % 8];
                if (rc.canMove(d)) return d;
            }
        }
        return bestDir;
    }

    // Logika Splasher
    public static void runSplasher(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        updateStuckDetection(rc);

        if (rc.getPaint() < SPLASHER_REFILL_THRESHOLD) {
            if (!tryWithdrawFromNearbyTower(rc))
                moveTowards(rc, findNearestTower(rc));
            return;
        }

        if (rc.isActionReady()) {
            MapLocation best = findBestSplashCenter(rc, nearbyTiles);
            if (best != null && rc.canAttack(best)) {
                boolean useSecondary = (rc.senseMapInfo(best).getMark() == PaintType.ALLY_SECONDARY);
                rc.attack(best, useSecondary);
            } else {
                MapLocation fallback = bestFallbackSplashTarget(rc, nearbyTiles);
                if (fallback != null && rc.canAttack(fallback)) {
                    boolean useSecondary = (rc.senseMapInfo(fallback).getMark() == PaintType.ALLY_SECONDARY);
                    rc.attack(fallback, useSecondary);
                }
            }
        }

        if (rc.isMovementReady()) {
            if (exploreTarget == null || rc.getLocation().distanceSquaredTo(exploreTarget) <= 4 || stuckCount > 3) {
                exploreTarget = computeExploreTarget(rc);
                if(stuckCount > 3) stuckCount = 0;
            }
            Direction dir = findBestSplasherDir(rc, nearbyTiles);
            if (dir != null && rc.canMove(dir)) rc.move(dir);
        }
    }

    static MapLocation findBestSplashCenter(RobotController rc, MapInfo[] nearbyTiles) {
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null; int bestScore = 0;
        for (MapInfo cand : nearbyTiles) {
            if (Clock.getBytecodesLeft() < 1500) break;
            
            MapLocation cLoc = cand.getMapLocation();
            if (myLoc.distanceSquaredTo(cLoc) > SPLASHER_ATTACK_RANGE_SQ) continue;

            if (!rc.canAttack(cLoc)) continue;
            
            int score = 0;

            for (MapInfo tile : nearbyTiles) {
                int d = cLoc.distanceSquaredTo(tile.getMapLocation());
                if (d > 4) continue; // Area splash rad^2 = 4
                PaintType p = tile.getPaint();
                if (p == PaintType.EMPTY) score += 5;
                else if (p.isEnemy() && d <= 2) score += 6;
                else if (p.isEnemy()) score += 3;
                else if (p.isAlly()) score -= 1;
            }
            if (score > bestScore) { bestScore = score; best = cLoc; }
        }
        return best;
    }

    static MapLocation bestFallbackSplashTarget(RobotController rc, MapInfo[] nearbyTiles) {
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null; int bestScore = Integer.MIN_VALUE;
        for (MapInfo t : nearbyTiles) {
            if (Clock.getBytecodesLeft() < 1000) break;
            MapLocation loc = t.getMapLocation();

            if (myLoc.distanceSquaredTo(loc) > SPLASHER_ATTACK_RANGE_SQ) continue;

            if (!rc.canAttack(loc)) continue;
            
            int score = 0;

            for (MapInfo tile : nearbyTiles) {
                int d = loc.distanceSquaredTo(tile.getMapLocation());
                if (d > 4) continue;
                PaintType p = tile.getPaint();
                if (p == PaintType.EMPTY) score += 4;
                else if (p.isEnemy()) score += 3;
            }

            if (score > bestScore) { bestScore = score; best = loc; }
        }
        return best;
    }

    static Direction findBestSplasherDir(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();
        
        int startIndex = rc.getID() % 8;

        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(startIndex + i) % 8];
            if (!rc.canMove(dir)) continue;
            
            MapLocation next = myLoc.add(dir);
            int score = 0;
            
            if (Clock.getBytecodesLeft() > 2500) {
                for (MapInfo tile : nearbyTiles) {
                    if (next.distanceSquaredTo(tile.getMapLocation()) <= 8) {
                        PaintType p = tile.getPaint();
                        if (p == PaintType.EMPTY) score += 4;
                        else if (p.isEnemy()) score += 5;
                    }
                }
            }

            if (exploreTarget != null) {
                if (next.distanceSquaredTo(exploreTarget) < myLoc.distanceSquaredTo(exploreTarget)) 
                    score += 15;
            }

            if (rc.senseMapInfo(next).getPaint().isEnemy()) score -= 3;
            
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        
        if (bestDir == null) {
            for (int i = 0; i < 8; i++) {
                Direction d = directions[(startIndex + i) % 8];
                if (rc.canMove(d)) return d;
            }
        }
        return bestDir;
    }

    // Logika Mopper
    public static void runMopper(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (rc.getPaint() < MOPPER_REFILL_THRESHOLD) {
            if (!tryWithdrawFromNearbyTower(rc))
                moveTowards(rc, findNearestTower(rc));
            return;
        }

        if (rc.isActionReady() && rc.getPaint() > 60) {
            RobotInfo needy = findAllyNeedingPaint(rc, allies);
            if (needy != null) {
                int give = Math.min(rc.getPaint() - MOPPER_REFILL_THRESHOLD,
                    needy.getType().paintCapacity - needy.getPaintAmount());
                if (give > 0 && rc.canTransferPaint(needy.getLocation(), give))
                    rc.transferPaint(needy.getLocation(), give);
            }
        }

        if (rc.isActionReady()) {
            Direction swing = findBestMopSwing(rc, enemies);
            if (swing != null && rc.canMopSwing(swing)) { rc.mopSwing(swing); return; }
        }

        if (rc.isActionReady()) {
            MapLocation mopTgt = findBestMopTarget(rc, nearbyTiles);
            if (mopTgt != null && rc.canAttack(mopTgt)) rc.attack(mopTgt);
        }

        if (rc.isMovementReady()) {
            Direction dir = findMopperDir(rc, allies, enemies, nearbyTiles);
            if (dir != null && rc.canMove(dir)) rc.move(dir);
        }
    }

    static RobotInfo findAllyNeedingPaint(RobotController rc, RobotInfo[] allies) {
        RobotInfo worst = null; double worstR = 0.4;

        for (RobotInfo r : allies) {
            if (r.getType().isTowerType()) continue;
            if (rc.getLocation().distanceSquaredTo(r.getLocation()) > 2) continue;
            double ratio = (double) r.getPaintAmount() / r.getType().paintCapacity;
            if (ratio < worstR) { worstR = ratio; worst = r; }
        }

        return worst;
    }

    static Direction findBestMopSwing(RobotController rc, RobotInfo[] enemies) {

        Direction bestDir = null; int bestScore = 0;
        MapLocation myLoc = rc.getLocation();

        for (Direction dir : cardinals) {
            int score = 0;
            MapLocation s1 = myLoc.add(dir), s2 = s1.add(dir);

            for (MapLocation c : perpLocs(s1, dir))
                for (RobotInfo e : enemies)
                    if (e.getLocation().equals(c))
                        score += 5 + (50 - Math.max(0, e.getPaintAmount()));

            for (MapLocation c : perpLocs(s2, dir))
                for (RobotInfo e : enemies)
                    if (e.getLocation().equals(c)) score += 3;

            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        return bestDir;
    }

    static MapLocation[] perpLocs(MapLocation center, Direction dir) {
        Direction l = dir.rotateLeft().rotateLeft();
        Direction r = dir.rotateRight().rotateRight();
        return new MapLocation[]{ center.add(l), center, center.add(r) };
    }

    static MapLocation findBestMopTarget(RobotController rc, MapInfo[] nearbyTiles) {
        MapLocation best = null; int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();
        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isEnemy() || !rc.canAttack(tile.getMapLocation())) continue;
            int score = 50 - myLoc.distanceSquaredTo(tile.getMapLocation());
            for (MapInfo s : nearbyTiles) {
                if (s.hasRuin() && tile.getMapLocation().distanceSquaredTo(s.getMapLocation()) <= 8)
                    score += 15;
            }
            if (score > bestScore) { bestScore = score; best = tile.getMapLocation(); }
        }
        return best;
    }

    static Direction findMopperDir(RobotController rc, RobotInfo[] allies, RobotInfo[] enemies, MapInfo[] nearbyTiles) throws GameActionException {
        if (enemies.length > 0) {
            RobotInfo closest = enemies[0]; int cd = Integer.MAX_VALUE;
            for (RobotInfo e : enemies) {
                int d = rc.getLocation().distanceSquaredTo(e.getLocation());
                if (d < cd) { cd = d; closest = e; }
            }
            Direction d = moveTowards(rc, closest.getLocation());
            if (d != null && rc.canMove(d)) return d;
        }
        for (RobotInfo r : allies) {
            if (r.getType() != UnitType.SOLDIER) continue;
            if (rc.getLocation().distanceSquaredTo(r.getLocation()) > 4) {
                Direction d = moveTowards(rc, r.getLocation());
                if (d != null && rc.canMove(d)) return d;
            }
        }
        
        Direction bestDir = null; int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();
        int startIndex = rc.getID() % 8;

        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(startIndex + i) % 8];
            if (!rc.canMove(dir)) continue;
            
            MapLocation next = myLoc.add(dir);
            int score = 0;
            
            if (Clock.getBytecodesLeft() > 2000) {
                for (MapInfo tile : nearbyTiles) {
                    if (next.distanceSquaredTo(tile.getMapLocation()) <= 4) {
                        if (tile.getPaint().isAlly()) score += 2;
                        else if (tile.getPaint().isEnemy()) score -= 3;
                    }
                }
            }
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        
        if (bestDir == null) {
            for (int i = 0; i < 8; i++) {
                Direction d = directions[(startIndex + i) % 8];
                if (rc.canMove(d)) return d;
            }
        }
        return bestDir;
    }

    // Target serangan terbaik
    static MapLocation findBestAttackTarget(RobotController rc, MapInfo[] nearbyTiles) {
        MapLocation best = null; int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();
        MapLocation nearestRuin = null; int nearestRuinDist = Integer.MAX_VALUE;

        for (MapInfo t : nearbyTiles) {
            if (Clock.getBytecodesLeft() < 2000) break;

            if (!t.hasRuin()) continue;

            int d = myLoc.distanceSquaredTo(t.getMapLocation());

            if (d < nearestRuinDist) { nearestRuinDist = d; nearestRuin = t.getMapLocation(); }
        }
        for (MapInfo tile : nearbyTiles) {
            if (Clock.getBytecodesLeft() < 1000) break;

            MapLocation loc = tile.getMapLocation();

            if (!rc.canAttack(loc)) continue;

            PaintType paint = tile.getPaint();
            if (paint.isAlly()) continue;

            int score = (paint == PaintType.EMPTY) ? 10 : 5;

            if (nearestRuin != null && loc.distanceSquaredTo(nearestRuin) <= 8) score += 3;

            if (tile.getMark() != PaintType.EMPTY && tile.getMark() != paint) score += 5;

            if (score > bestScore) { bestScore = score; best = loc; }
        }
        return best;
    }

    // Utility
    static void paintCurrentTile(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo cur = rc.senseMapInfo(rc.getLocation());
        if (!cur.getPaint().isAlly() && rc.canAttack(rc.getLocation()))
            rc.attack(rc.getLocation());
    }

    static void updateStuckDetection(RobotController rc) {
        MapLocation cur = rc.getLocation();
        pastLocations[pastLocIdx] = cur;
        pastLocIdx = (pastLocIdx + 1) % 4;

        int distinctLocs = 0;
        for (int i = 0; i < 4; i++) {
            if (pastLocations[i] != null) {
                boolean unique = true;
                for (int j = 0; j < i; j++) {
                    if (pastLocations[i].equals(pastLocations[j])) {
                        unique = false; break;
                    }
                }
                if (unique) distinctLocs++;
            }
        }
        
        if (pastLocations[3] != null && distinctLocs <= 2) {
            stuckCount++;
        } else {
            stuckCount = 0;
        }

        if (stuckCount > 3) {
            for (int i = 0; i < 4; i++) pastLocations[i] = null;
        }
    }

    static Direction moveTowards(RobotController rc, MapLocation target) {
        if (target == null) return null;
        Direction dir = rc.getLocation().directionTo(target);

        if (rc.canMove(dir)) return dir;
        Direction l = dir.rotateLeft(), r = dir.rotateRight();

        if (rc.canMove(l)) return l;
        if (rc.canMove(r)) return r;
        if (rc.canMove(l.rotateLeft()))  return l.rotateLeft();
        if (rc.canMove(r.rotateRight())) return r.rotateRight();

        return null; 
    }

    static Direction moveTowardsWithAvoidance(RobotController rc, MapLocation target, MapInfo[] nearbyTiles) throws GameActionException {
        if (target == null) return null;
        Direction bestDir = null; int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();
        
        int startIndex = rc.getID() % 8;

        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(startIndex + i) % 8];
            if (!rc.canMove(dir)) continue;
            
            MapLocation next = myLoc.add(dir);
            int score = 0;

            if (next.distanceSquaredTo(target) < myLoc.distanceSquaredTo(target))
                score += 20;

            if (Clock.getBytecodesLeft() > 3000) {    
                for (MapInfo tile : nearbyTiles) {
                    if (next.distanceSquaredTo(tile.getMapLocation()) <= 4) {
                        if (tile.getPaint() == PaintType.EMPTY) score += 1;
                    }
                }
            }
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        return bestDir;
    }

    static MapLocation findNearestTower(RobotController rc) throws GameActionException {
        MapLocation best = homeTower;
        int bestDist = best != null
            ? rc.getLocation().distanceSquaredTo(best) : Integer.MAX_VALUE;
        for (RobotInfo r : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!r.getType().isTowerType()) continue;
            int d = rc.getLocation().distanceSquaredTo(r.getLocation());
            if (d < bestDist) {
                bestDist = d; best = r.getLocation(); homeTower = r.getLocation();
            }
        }
        return best;
    }

    static boolean tryWithdrawFromNearbyTower(RobotController rc) throws GameActionException {
        for (RobotInfo r : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!r.getType().isTowerType()) continue;
            if (rc.getLocation().distanceSquaredTo(r.getLocation()) > 2) continue;
            int need  = rc.getType().paintCapacity - rc.getPaint();
            int avail = Math.min(need, r.getPaintAmount());
            if (avail > 0 && rc.canTransferPaint(r.getLocation(), -avail)) {
                rc.transferPaint(r.getLocation(), -avail);
                return true;
            }
        }
        return false;
    }
}
