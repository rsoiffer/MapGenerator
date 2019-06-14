package game;

import behaviors.FPSBehavior;
import behaviors.QuitOnEscapeBehavior;
import engine.Core;
import static engine.Core.dt;
import engine.Input;
import static engine.Layer.UPDATE;
import engine.Settings;
import graphics.Camera;
import static graphics.Camera.camera3d;
import graphics.Color;
import static graphics.opengl.GLObject.bindAll;
import graphics.opengl.Shader;
import graphics.opengl.Texture;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import util.Mutable;
import util.Noise;
import static util.math.MathUtils.ceil;
import static util.math.MathUtils.clamp;
import static util.math.MathUtils.floor;
import static util.math.MathUtils.lerp;
import util.math.Quaternion;
import util.math.Transformation;
import util.math.Vec2d;
import util.math.Vec3d;

public class MapGenerator {

    public static final int WIDTH = 256, HEIGHT = 256;
    public static Texture landColor, landHeight, waterColor, waterHeight;
    public static Shader shader;

    public static double[][] b;
    public static double[][] bInit;
    public static double[][] d;
    public static double[][] s;
    public static double[][][] f;
    public static double[][] vx;
    public static double[][] vy;
    public static Noise noise;
    public static List<Vec2d> sources;

    public static double zScale = 100;
    public static double dt = .05;
    public static double rain = .0001;
    public static double numSources = .01;
    public static double sourceStrength = .1 * 0;
    public static double numDrops = .01;
    public static double dropStrength = .1 * 0;
    public static double pipeArea = 5;
    public static double sedimentCapacity = .005;
    public static double dissolving = .1;
    public static double deposition = .1;
    public static double evaporation = .01;

    public static void main(String[] args) {
        Settings.ENABLE_VSYNC = false;
        Settings.SHOW_CURSOR = false;
        Core.init();

        new FPSBehavior().create();
        new QuitOnEscapeBehavior().create();

        landColor = new Texture(GL_TEXTURE_2D);
        //landColor.setParameter(GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        landHeight = new Texture(GL_TEXTURE_2D);
        landHeight.num = 1;

        waterColor = new Texture(GL_TEXTURE_2D);
        waterColor.setParameter(GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        waterHeight = new Texture(GL_TEXTURE_2D);
        waterHeight.num = 1;

        shader = Shader.load("terrain");
        shader.setUniform("tex", 0);
        shader.setUniform("height", 1);

        CustomModel plane = new CustomModel();
        for (int x = 0; x < HEIGHT - 1; x++) {
            for (int y = 0; y < HEIGHT - 1; y++) {
                double x2 = (x + .5) / WIDTH, x3 = (x + 1.5) / WIDTH;
                double y2 = (y + .5) / HEIGHT, y3 = (y + 1.5) / HEIGHT;
                plane.addTriangle(new Vec3d(x2, y2, 0), new Vec2d(x2, y2), new Vec3d(x3, y2, 0), new Vec2d(x3, y2), new Vec3d(x3, y3, 0), new Vec2d(x3, y3));
                plane.addTriangle(new Vec3d(x2, y2, 0), new Vec2d(x2, y2), new Vec3d(x2, y3, 0), new Vec2d(x2, y3), new Vec3d(x3, y3, 0), new Vec2d(x3, y3));
            }
        }
        plane.createVAO();

        randomizeMap();

        Mutable<Integer> step = new Mutable(0);
        UPDATE.onStep(() -> {
            cameraControls();
            Camera.current = Camera.camera3d;

            if (Input.keyJustPressed(GLFW_KEY_R)) {
                randomizeMap();
            }

            step.o++;
            // erode(1, .2 * Math.pow(1 + Math.sin(step.o * .01), 3));
            erode(1, 1);
            updateMap();

            shader.setMVP(Transformation.create(new Vec3d(0, 0, 0), Quaternion.IDENTITY, new Vec3d(WIDTH, HEIGHT, zScale)));
            shader.setUniform("color", Color.WHITE);
            bindAll(landColor, landHeight, shader);
            plane.render();
            bindAll(waterColor, waterHeight, shader);
            plane.render();
        });

        Core.run();
    }

    private static void randomizeMap() {
        b = new double[WIDTH][HEIGHT];
        bInit = new double[WIDTH][HEIGHT];
        d = new double[WIDTH][HEIGHT];
        s = new double[WIDTH][HEIGHT];
        f = new double[WIDTH][HEIGHT][4];
        vx = new double[WIDTH][HEIGHT];
        vy = new double[WIDTH][HEIGHT];
        noise = new Noise(new Random());
        sources = new LinkedList();
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                b[x][y] = noise.fbm2d(x, y, 8, .002);
                // / (1 + new Vec2d(x, y).sub(128).lengthSquared() * 1e-4);
                if (Math.random() < numSources) {
                    sources.add(new Vec2d(x, y));
                }
            }
        }

        double total = 0;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                total += b[x][y] + s[x][y];
            }
        }
        total /= .5 * WIDTH * HEIGHT;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                b[x][y] /= total;
                s[x][y] /= total;
            }
        }
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                bInit[x][y] = b[x][y];
            }
        }
        updateMap();
    }

    private static void erode(int steps, double rainMult) {
        for (int i = 0; i < steps; i++) {
            double[][] d1 = new double[WIDTH][HEIGHT];
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    d1[x][y] = d[x][y] + dt * rain * rainMult;
                    if (Math.random() < numDrops * dt * rainMult) {
                        d1[x][y] += dropStrength;
                    }
                }
            }
            for (Vec2d v : sources) {
                d1[floor(v.x)][floor(v.y)] += dt * sourceStrength * rainMult;
            }

            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    double dhL = x > 0 ? (b[x][y] + d[x][y] - b[x - 1][y] - d[x - 1][y]) : 0;
                    double dhR = x < WIDTH - 1 ? (b[x][y] + d[x][y] - b[x + 1][y] - d[x + 1][y]) : 0;
                    double dhT = y < HEIGHT - 1 ? (b[x][y] + d[x][y] - b[x][y + 1] - d[x][y + 1]) : 0;
                    double dhB = y > 0 ? (b[x][y] + d[x][y] - b[x][y - 1] - d[x][y - 1]) : 0;
                    f[x][y][0] = Math.max(0, f[x][y][0] + dt * pipeArea * dhL);
                    f[x][y][1] = Math.max(0, f[x][y][1] + dt * pipeArea * dhR);
                    f[x][y][2] = Math.max(0, f[x][y][2] + dt * pipeArea * dhT);
                    f[x][y][3] = Math.max(0, f[x][y][3] + dt * pipeArea * dhB);
                    double totalFlow = f[x][y][0] + f[x][y][1] + f[x][y][2] + f[x][y][3];
                    double K = Math.min(1, d1[x][y] / (Math.max(totalFlow, 1e-6) * dt));
                    f[x][y][0] *= K;
                    f[x][y][1] *= K;
                    f[x][y][2] *= K;
                    f[x][y][3] *= K;
                }
            }

            double[][] d2 = new double[WIDTH][HEIGHT];
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    double flowIn = (x > 0 ? f[x - 1][y][1] : 0) + (x < WIDTH - 1 ? f[x + 1][y][0] : 0)
                            + (y < HEIGHT - 1 ? f[x][y + 1][3] : 0) + (y > 0 ? f[x][y - 1][2] : 0);
                    double flowOut = f[x][y][0] + f[x][y][1] + f[x][y][2] + f[x][y][3];
                    double dv = dt * (flowIn - flowOut);
                    d2[x][y] = d1[x][y] + dv;
                    double dbar = (d1[x][y] + d1[x][y]) / 2;
                    double dwx = ((x > 0 ? f[x - 1][y][1] : 0) - f[x][y][0] + f[x][y][1] - (x < WIDTH - 1 ? f[x + 1][y][0] : 0)) / 2;
                    vx[x][y] = dwx / (dbar + 1e-5);
                    double dwy = ((y > 0 ? f[x][y - 1][2] : 0) - f[x][y][3] + f[x][y][2] - (y < HEIGHT - 1 ? f[x][y + 1][3] : 0)) / 2;
                    vy[x][y] = dwy / (dbar + 1e-5);
                }
            }

            double[][] tiltAngle = new double[WIDTH][HEIGHT];
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    double dhL = x > 0 ? (b[x][y] + d[x][y] - b[x - 1][y] - d[x - 1][y]) : 0;
                    double dhR = x < WIDTH - 1 ? (b[x][y] + d[x][y] - b[x + 1][y] - d[x + 1][y]) : 0;
                    double dhT = y < HEIGHT - 1 ? (b[x][y] + d[x][y] - b[x][y + 1] - d[x][y + 1]) : 0;
                    double dhB = y > 0 ? (b[x][y] + d[x][y] - b[x][y - 1] - d[x][y - 1]) : 0;
                    tiltAngle[x][y] = .1 + Math.atan(zScale * new Vec2d(dhL - dhR, dhB - dhT).length() / 2);
                }
            }

            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    double C = sedimentCapacity * Math.sin(tiltAngle[x][y]) * speed(x, y);
                    C *= Math.min(1e2 * d[x][y], 1);
                    // C *= clamp(1 - d[x][y] * 10, 0, 1);
                    C *= Math.exp(-1 * d[x][y]);
                    if (C > s[x][y]) {
                        b[x][y] -= dt * dissolving * (C - s[x][y]);
                        d2[x][y] += dt * dissolving * (C - s[x][y]);
                        s[x][y] += dt * dissolving * (C - s[x][y]);
                    } else {
                        b[x][y] += dt * deposition * (s[x][y] - C);
                        d2[x][y] -= dt * deposition * (s[x][y] - C);
                        s[x][y] -= dt * deposition * (s[x][y] - C);
                        if (d2[x][y] < 0) {
                            d2[x][y] = 0;
                        }
                    }
                }
            }

            double[][] b2 = new double[WIDTH][HEIGHT];
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    double dhL = x > 0 ? (b[x][y] + -b[x - 1][y]) : 0;
                    double dhR = x < WIDTH - 1 ? (b[x][y] - b[x + 1][y]) : 0;
                    double dhT = y < HEIGHT - 1 ? (b[x][y] - b[x][y + 1]) : 0;
                    double dhB = y > 0 ? (b[x][y] - b[x][y - 1]) : 0;
                    double alpha = Math.tan(Math.PI / 3) / zScale * Math.exp(-d2[x][y]);
                    b2[x][y] = b[x][y] - dt * (clamp(0, dhL - alpha, dhL + alpha)
                            + clamp(0, dhR - alpha, dhR + alpha)
                            + clamp(0, dhT - alpha, dhT + alpha)
                            + clamp(0, dhB - alpha, dhB + alpha));
                }
            }
            b = b2;

            double[][] s2 = new double[WIDTH][HEIGHT];
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    double x2 = clamp(x - vx[x][y] * dt, .01, WIDTH - 1.01);
                    double y2 = clamp(y - vy[x][y] * dt, .01, HEIGHT - 1.01);
                    double s00 = s[floor(x2)][floor(y2)];
                    double s10 = s[ceil(x2)][floor(y2)];
                    double s01 = s[floor(x2)][ceil(y2)];
                    double s11 = s[ceil(x2)][ceil(y2)];
                    double s_0 = lerp(s00, s10, x2 - floor(x2));
                    double s_1 = lerp(s01, s11, x2 - floor(x2));
                    s2[x][y] = lerp(s_0, s_1, y2 - floor(y2));
                }
            }
            s = s2;

            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    d[x][y] = d2[x][y] * (1 - evaporation * dt);
                    for (int j = 0; j < 4; j++) {
                        f[x][y][j] *= (1 - evaporation * dt);
                    }
                }
            }

            double total = 0;
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    total += b[x][y] + s[x][y];
                }
            }
            total /= .5 * WIDTH * HEIGHT;
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    b[x][y] /= total;
                    s[x][y] /= total;
                }
            }
        }
    }

    private static double speed(int x, int y) {
        return Math.sqrt(vx[x][y] * vx[x][y] + vy[x][y] * vy[x][y]);
    }

    private static void updateMap() {
        Color[][] landColorMap = new Color[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                double dhL = x > 0 ? (b[x][y] + -b[x - 1][y]) : 0;
                double dhR = x < WIDTH - 1 ? (b[x][y] - b[x + 1][y]) : 0;
                double dhT = y < HEIGHT - 1 ? (b[x][y] - b[x][y + 1]) : 0;
                double dhB = y > 0 ? (b[x][y] - b[x][y - 1]) : 0;
                double curvature = dhL + dhR + dhT + dhB;
                double c = .3 + .4 * b[x][y] + curvature;
                landColorMap[x][y] = new Color(c + 1 * (b[x][y] - bInit[x][y]), c, .1, 1);
                // landColorMap[x][y] = new Color(.1 + .8 * b[x][y], .1 + .8 * b[x][y], .1, 1);
            }
        }
        uploadMap(landColorMap, landColor);

        Color[][] landHeightMap = new Color[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                landHeightMap[x][y] = new Color(b[x][y], 0, 0, 0);
            }
        }
        uploadMap(landHeightMap, landHeight);

        Color[][] waterColorMap = new Color[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                waterColorMap[x][y] = new Color(speed(x, y) * .1, .2 + s[x][y] * 50, 1, clamp(100 * d[x][y], 0, .5));
            }
        }
        uploadMap(waterColorMap, waterColor);

        Color[][] waterHeightMap = new Color[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                waterHeightMap[x][y] = new Color(b[x][y] + d[x][y] + 1e-4, 0, 0, 0);
            }
        }
        uploadMap(waterHeightMap, waterHeight);
    }

    private static void uploadMap(Color[][] map, Texture tex) {
        ByteBuffer bb = memAlloc(16 * WIDTH * HEIGHT);
        bb.clear();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                bb.putFloat((float) clamp(map[x][y].r, 0, 1));
                bb.putFloat((float) clamp(map[x][y].g, 0, 1));
                bb.putFloat((float) clamp(map[x][y].b, 0, 1));
                bb.putFloat((float) clamp(map[x][y].a, 0, 1));
            }
        }
        bb.flip();
        // tex.uploadData(WIDTH, HEIGHT, bb);
        tex.bind();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, WIDTH, HEIGHT, 0, GL_RGBA, GL_FLOAT, bb);
        glGenerateMipmap(GL_TEXTURE_2D);
        memFree(bb);
    }

    private static void cameraControls() {
        camera3d.horAngle -= Input.mouseDelta().x * 16. / 3;
        camera3d.vertAngle -= Input.mouseDelta().y * 3;
        camera3d.vertAngle = clamp(camera3d.vertAngle, -1.55, 1.55);

        double flySpeed = 50;
        Vec3d vel = new Vec3d(0, 0, 0);
        if (Input.keyDown(GLFW_KEY_W)) {
            vel = vel.add(camera3d.facing().setLength(flySpeed));
        }
        if (Input.keyDown(GLFW_KEY_A)) {
            vel = vel.add(camera3d.facing().cross(camera3d.up).setLength(-flySpeed));
        }
        if (Input.keyDown(GLFW_KEY_S)) {
            vel = vel.add(camera3d.facing().setLength(-flySpeed));
        }
        if (Input.keyDown(GLFW_KEY_D)) {
            vel = vel.add(camera3d.facing().cross(camera3d.up).setLength(flySpeed));
        }
        if (Input.keyDown(GLFW_KEY_SPACE)) {
            vel = vel.add(camera3d.up.setLength(flySpeed));
        }
        if (Input.keyDown(GLFW_KEY_LEFT_SHIFT)) {
            vel = vel.add(camera3d.up.setLength(-flySpeed));
        }
        camera3d.position = camera3d.position.add(vel.mul(dt()));
    }
}
