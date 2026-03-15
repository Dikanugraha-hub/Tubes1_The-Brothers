package alternatif1bot;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

    static final Direction[] DIRECTIONS = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    static final Random rng = new Random(6147);

    static final int SOLDIER_REFILL_THRESHOLD = 45;
    static final int SPLASHER_REFILL_THRESHOLD = 80;
    static final int MOPPER_REFILL_THRESHOLD = 30;

    static MapLocation homeTower = null;
    static MapLocation exploreTarget = null;
    static Direction lastMoveDir = null;
    static int stuckTurns = 0;
    static MapLocation lastLoc = null;
    static int spawnCounter = 0;
    static int laneId = -1;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        if (laneId == -1) {
            laneId = rc.getID() % 8;
        }


        if (homeTower == null) {
            homeTower = detectNearestTower(rc);
        }

        while (true) {
            try {
                switch (rc.getType()) {
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
            } catch (GameActionException e) {
                System.out.println("GAE: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("ERR: " + e.getMessage());
                e.printStackTrace();

            } finally {
                Clock.yield();
            }
        }
    }

    static void runTower(RobotController rc) throws GameActionException {
        attackNearestEnemy(rc);


        UnitType toBuild;
        int mod = spawnCounter % 8;
        if (mod == 6) {
            toBuild = UnitType.MOPPER;
        } else if (mod == 7) {
            toBuild = UnitType.SPLASHER;
        } else {
            toBuild = UnitType.SOLDIER;
        }

        if (tryBuild(rc, toBuild)) {
            spawnCounter++;
        } else if (toBuild != UnitType.SOLDIER && tryBuild(rc, UnitType.SOLDIER)) {
            spawnCounter++;
        }
    }

    static boolean tryBuild(RobotController rc, UnitType type) throws GameActionException {
        int start = rng.nextInt(DIRECTIONS.length);
        for (int i = 0; i < DIRECTIONS.length; i++) {
            Direction d = DIRECTIONS[(start + i) % DIRECTIONS.length];
            MapLocation loc = rc.getLocation().add(d);
            if (rc.canBuildRobot(type, loc)) {
                rc.buildRobot(type, loc);
                return true;
            }
        }
        return false;
    }

    static void runSoldier(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        updateStuck(rc);

        if (rc.getPaint() < SOLDIER_REFILL_THRESHOLD) {
            doRefillBehavior(rc);
            return;
        }

        if (rc.isActionReady()) {
            MapLocation bestAttack = chooseBestPaintTarget(rc, rc.getLocation(), nearbyTiles);
            if (bestAttack != null && rc.canAttack(bestAttack)) {
                boolean useSecondary = shouldUseSecondary(rc, bestAttack);
                rc.attack(bestAttack, useSecondary);

            } else if (rc.canAttack(rc.getLocation()) && !rc.senseMapInfo(rc.getLocation()).getPaint().isAlly()) {
                rc.attack(rc.getLocation());

            }
        }

        if (rc.isMovementReady()) {
            if (exploreTarget == null || rc.getLocation().distanceSquaredTo(exploreTarget) <= 5 || stuckTurns >= 4) {
                exploreTarget = computeLaneTarget(rc);
                stuckTurns = 0;

            }

            Direction bestDir = chooseBestMoveDirection(rc, nearbyTiles);
            if (bestDir != null && rc.canMove(bestDir)) {
                rc.move(bestDir);
                lastMoveDir = bestDir;
            }
        }

        if (rc.isActionReady()) {
            paintCurrentTileIfNeeded(rc);
        }
    }

    static void runSplasher(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        if (rc.getPaint() < SPLASHER_REFILL_THRESHOLD) {
            doRefillBehavior(rc);
            return;
        }

        if (rc.isActionReady()) {
            MapLocation best = chooseBestSplashTarget(rc, nearbyTiles);
            if (best != null && rc.canAttack(best)) {
                rc.attack(best);
            }
        }

        if (rc.isMovementReady()) {
            Direction d = chooseBestMoveDirection(rc, nearbyTiles);
            if (d != null && rc.canMove(d)) {
                rc.move(d);
                lastMoveDir = d;
            }
        }
    }

    static void runMopper(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        if (rc.getPaint() < MOPPER_REFILL_THRESHOLD) {
            doRefillBehavior(rc);

            return;
        }

        if (rc.isActionReady()) {
            Direction bestSwing = findBestMopSwing(rc);
            if (bestSwing != null && rc.canMopSwing(bestSwing)) {
                rc.mopSwing(bestSwing);
                return;
            }

            MapLocation enemyPaint = findNearestEnemyPaint(rc, nearbyTiles);
            if (enemyPaint != null && rc.canAttack(enemyPaint)) {
                rc.attack(enemyPaint);
            }
        }

        if (rc.isMovementReady()) {
            Direction d = chooseBestMoveDirection(rc, nearbyTiles);
            if (d != null && rc.canMove(d)) {
                rc.move(d);
                lastMoveDir = d;
            }
        }
    }

    static MapLocation chooseBestPaintTarget(RobotController rc, MapLocation from, MapInfo[] nearbyTiles) {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        boolean fromCurrentLocation = from.equals(rc.getLocation());

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (from.distanceSquaredTo(loc) > 9) {

                continue;
            }
            if (fromCurrentLocation && !rc.canAttack(loc)) {
                continue;
            }

            PaintType paint = tile.getPaint();
            if (paint.isAlly()) {

                continue;
            }

            int score = 0;
            if (paint == PaintType.EMPTY) {
                score += 30;
            } else if (paint.isEnemy()) {
                score += 22;

            } else {
                score += 8;
            }

            int frontier = countNonAllyNeighbors(loc, nearbyTiles);
            score += frontier * 4;

            if (homeTower != null) {
                score += Math.min(12, loc.distanceSquaredTo(homeTower) / 12);
            }

            if (exploreTarget != null) {
                int cur = from.distanceSquaredTo(exploreTarget);
                int nxt = loc.distanceSquaredTo(exploreTarget);
                if (nxt < cur) {
                    score += 4;

                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }

    static Direction chooseBestMoveDirection(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction d : DIRECTIONS) {
            if (!rc.canMove(d)) {
                continue;
            }

            MapLocation next = myLoc.add(d);
            int score = 0;

            MapLocation projectedAttack = chooseBestPaintTarget(rc, next, nearbyTiles);
            if (projectedAttack != null) {
                score += scoreProjectedAttack(next, projectedAttack, nearbyTiles);
            } else {
                score -= 3;
            }

            score += countNonAllyInRadius(next, nearbyTiles, 8) * 2;

            if (exploreTarget != null) {
                int curDist = myLoc.distanceSquaredTo(exploreTarget);
                int nextDist = next.distanceSquaredTo(exploreTarget);
                if (nextDist < curDist) {
                    score += 10;
                } else if (nextDist > curDist) {
                    score -= 2;
                }
            }

            if (lastMoveDir != null && d == lastMoveDir) {
                score += 2;

            }

            if (rc.senseMapInfo(next).getPaint().isAlly()) {
                score -= 2;
            }

            int crowd = countNearbyAllies(rc, next);
            score -= crowd * 2;

            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }
        if (bestDir == null) {
            int start = rng.nextInt(DIRECTIONS.length);
            for (int i = 0; i < DIRECTIONS.length; i++) {
                Direction d = DIRECTIONS[(start + i) % DIRECTIONS.length];
                if (rc.canMove(d)) {
                    return d;
                }
            }
        }
        return bestDir;
    }



    static int scoreProjectedAttack(MapLocation from, MapLocation target, MapInfo[] nearbyTiles) {
        int base = 0;
        int frontier = countNonAllyNeighbors(target, nearbyTiles);
        base += frontier * 5;
        base += 15 - Math.min(12, from.distanceSquaredTo(target));
        return base;
    }

    static int countNonAllyNeighbors(MapLocation center, MapInfo[] nearbyTiles) {
        int c = 0;
        for (MapInfo tile : nearbyTiles) {
            int d = center.distanceSquaredTo(tile.getMapLocation());
            if (d <= 2 && !tile.getPaint().isAlly()) {
                c++;
            }
        }
        return c;
    }

    static int countNonAllyInRadius(MapLocation center, MapInfo[] nearbyTiles, int radiusSq) {
        int c = 0;
        for (MapInfo tile : nearbyTiles) {
            if (center.distanceSquaredTo(tile.getMapLocation()) <= radiusSq && !tile.getPaint().isAlly()) {
                c++;
            }
        }
        return c;
    }

    static int countNearbyAllies(RobotController rc, MapLocation center) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int c = 0;
        for (RobotInfo a : allies) {
            if (a.getType().isTowerType()) {
                continue;
            }
            if (center.distanceSquaredTo(a.getLocation()) <= 4) {
                c++;
            }
        }
        return c;
    }

    static boolean shouldUseSecondary(RobotController rc, MapLocation loc) throws GameActionException {
        MapInfo info = rc.senseMapInfo(loc);
        PaintType mark = info.getMark();
        return mark == PaintType.ALLY_SECONDARY;
    }

    static void paintCurrentTileIfNeeded(RobotController rc) throws GameActionException {
        MapLocation cur = rc.getLocation();
        if (!rc.canSenseLocation(cur)) {
            return;
        }
        MapInfo info = rc.senseMapInfo(cur);
        if (!info.getPaint().isAlly() && rc.canAttack(cur)) {
            rc.attack(cur);
        }
    }

    static MapLocation chooseBestSplashTarget(RobotController rc, MapInfo[] nearbyTiles) {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo cand : nearbyTiles) {
            MapLocation center = cand.getMapLocation();
            if (myLoc.distanceSquaredTo(center) > 4 || !rc.canAttack(center)) {
                continue;
            }
            int score = 0;
            for (MapInfo tile : nearbyTiles) {
                if (center.distanceSquaredTo(tile.getMapLocation()) > 4) {
                    continue;
                }
                PaintType p = tile.getPaint();
                if (p == PaintType.EMPTY) {
                    score += 3;
                } else if (p.isEnemy()) {
                    score += 5;
                } else {
                    score -= 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = center;
            }
        }
        return best;
    }

    static Direction findBestMopSwing(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) {
            return null;
        }

        Direction[] cardinals = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        Direction best = null;
        int bestScore = 0;

        MapLocation me = rc.getLocation();

        for (Direction d : cardinals) {
            int score = 0;
            MapLocation p1 = me.add(d);
            MapLocation p2 = p1.add(d);

            for (RobotInfo e : enemies) {
                int a = p1.distanceSquaredTo(e.getLocation());
                int b = p2.distanceSquaredTo(e.getLocation());
                if (a <= 2) {
                    score += 4;

                }
                if (b <= 2) {
                    score += 2;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    static MapLocation findNearestEnemyPaint(RobotController rc, MapInfo[] nearbyTiles) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();
        for (MapInfo t : nearbyTiles) {
            if (!t.getPaint().isEnemy()) {
                continue;
            }
            MapLocation loc = t.getMapLocation();
            if (!rc.canAttack(loc)) {
                continue;
            }
            int d = myLoc.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }
        return best;
    }

    static void doRefillBehavior(RobotController rc) throws GameActionException {
        if (tryWithdrawFromNearbyTower(rc)) {

            return;
        }

        MapLocation tower = detectNearestTower(rc);
        if (tower == null) {
            Direction random = DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
            if (rc.isMovementReady() && rc.canMove(random)) {
                rc.move(random);
            }
            return;
        }

        if (rc.isMovementReady()) {
            Direction d = moveTowards(rc, tower);
            if (d != null && rc.canMove(d)) {
                rc.move(d);
            }
        }
    }

    static Direction moveTowards(RobotController rc, MapLocation target) {
        if (target == null) {
            return null;
        }
        Direction d = rc.getLocation().directionTo(target);
        if (rc.canMove(d)) {

            return d;
        }
        Direction l = d.rotateLeft();
        Direction r = d.rotateRight();
        if (rc.canMove(l)) {
            return l;
        }
        if (rc.canMove(r)) {
            return r;
        }
        Direction ll = l.rotateLeft();
        Direction rr = r.rotateRight();
        if (rc.canMove(ll)) {
            return ll;
        }
        if (rc.canMove(rr)) {
            return rr;
        }
        return null;
    }

    static MapLocation detectNearestTower(RobotController rc) throws GameActionException {
        MapLocation best = homeTower;
        int bestDist = (best == null) ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(best);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo a : allies) {
            if (!a.getType().isTowerType()) {
                continue;

            }
            int d = rc.getLocation().distanceSquaredTo(a.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = a.getLocation();
            }
        }
        if (best != null) {
            homeTower = best;
        }
        return best;
    }

    static boolean tryWithdrawFromNearbyTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo a : allies) {
            if (!a.getType().isTowerType()) {
                continue;
            }
            if (rc.getLocation().distanceSquaredTo(a.getLocation()) > 2) {
                continue;

            }
            int need = rc.getType().paintCapacity - rc.getPaint();
            int available = Math.min(need, a.getPaintAmount());
            if (available > 0 && rc.canTransferPaint(a.getLocation(), -available)) {
                rc.transferPaint(a.getLocation(), -available);
                return true;
            }
        }
        return false;
    }

    static void attackNearestEnemy(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) {
            return;
        }
        RobotInfo weakest = enemies[0];
        for (RobotInfo e : enemies) {
            if (e.getHealth() < weakest.getHealth()) {
                weakest = e;
            }
        }
        if (rc.canAttack(weakest.getLocation())) {
            rc.attack(weakest.getLocation());
        }
    }

    static MapLocation computeLaneTarget(RobotController rc) {
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();
        int m = 3;

        MapLocation[] lanes = new MapLocation[] {
            new MapLocation(w - m, h - m),
            new MapLocation(m, h - m),
            new MapLocation(w - m, m),
            new MapLocation(m, m),
            new MapLocation(w / 2, h - m),
            new MapLocation(w / 2, m),
            new MapLocation(w - m, h / 2),
            new MapLocation(m, h / 2)
        };

        MapLocation base = lanes[laneId % lanes.length];
        if (stuckTurns >= 4) {
            int i = rng.nextInt(lanes.length);
            return lanes[i];
        }
        return base;
    }

    static void updateStuck(RobotController rc) {
        MapLocation cur = rc.getLocation();
        if (lastLoc != null && lastLoc.equals(cur)) {
            stuckTurns++;
            
        } else {
            stuckTurns = 0;
        }
        lastLoc = cur;
    }
}
