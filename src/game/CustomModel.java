package game;

import game.Vertex.VertexPBR;
import graphics.opengl.BufferObject;
import graphics.opengl.VertexArrayObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import util.math.MathUtils;
import util.math.Vec2d;
import util.math.Vec3d;

public class CustomModel implements Model {

    final List<VertexPBR> vertices = new ArrayList();
    private int numVertices;
    private BufferObject vbo;
    private VertexArrayObject vao;

    private final Vec3d randomDir = MathUtils.randomInSphere(new Random());

    public void addCylinder(Vec3d p, Vec3d dir, double radius, int detail, double texW, double texH0, double texH1) {
        Vec3d dir1 = dir.cross(randomDir).normalize();
        Vec3d dir2 = dir.cross(dir1).normalize();
        for (int i = 0; i < detail; i++) {
            double angle0 = i * 2 * Math.PI / detail, angle1 = (i + 1) * 2 * Math.PI / detail;
            Vec3d v0 = p.add(dir1.mul(Math.cos(angle0) * radius)).add(dir2.mul(Math.sin(angle0) * radius));
            Vec3d v1 = p.add(dir1.mul(Math.cos(angle1) * radius)).add(dir2.mul(Math.sin(angle1) * radius));
            addRectangle(v0, v1.sub(v0), dir, new Vec2d(texW * i / detail, texH0), new Vec2d(texW / detail, 0), new Vec2d(0, texH1 - texH0));
        }
    }

    public void addRectangle(Vec3d p, Vec3d edge1, Vec3d edge2, Vec2d uv, Vec2d uvd1, Vec2d uvd2) {
        addTriangle(p, uv, p.add(edge1), uv.add(uvd1), p.add(edge1).add(edge2), uv.add(uvd1).add(uvd2));
        addTriangle(p, uv, p.add(edge1).add(edge2), uv.add(uvd1).add(uvd2), p.add(edge2), uv.add(uvd2));
    }

    public void addTriangle(Vec3d p1, Vec2d uv1, Vec3d p2, Vec2d uv2, Vec3d p3, Vec2d uv3) {
        Vec3d edge1 = p2.sub(p1), edge2 = p3.sub(p1);
        Vec2d duv1 = uv2.sub(uv1), duv2 = uv3.sub(uv1);
        Vec3d normal = edge1.cross(edge2).normalize();
        Vec3d tangent = edge1.mul(duv2.y).add(edge2.mul(-duv1.y)).normalize();
        Vec3d bitangent = edge1.mul(-duv2.x).add(edge2.mul(duv1.x)).normalize();
        vertices.addAll(Arrays.asList(
                new VertexPBR(p1, uv1, normal, tangent, bitangent),
                new VertexPBR(p2, uv2, normal, tangent, bitangent),
                new VertexPBR(p3, uv3, normal, tangent, bitangent)
        ));
    }

    public void clear() {
        vertices.clear();
    }

    public CustomModel copy() {
        CustomModel m = new CustomModel();
        m.vertices.addAll(vertices);
        return m;
    }

    public void createVAO() {
        numVertices = vertices.size();
        vbo = Vertex.createVBO(vertices);
        vao = Vertex.createVAO(vbo, new int[]{3, 2, 3, 3, 3});
    }

    public int numTriangles() {
        return numVertices / 3;
    }

    @Override
    public void render() {
        vao.bind();
        glDrawArrays(GL_TRIANGLES, 0, numVertices);
    }

    public void smoothVertexNormals() {
        HashMap<Vec3d, Vec3d> normals = new HashMap();
        for (VertexPBR v : vertices) {
            normals.compute(v.position, (key, val) -> val == null ? v.normal : val.add(v.normal));
        }
        for (int i = 0; i < vertices.size(); i++) {
            VertexPBR v = vertices.get(i);
            VertexPBR v2 = new VertexPBR(v.position, v.texCoord, normals.get(v.position).normalize(), v.tangent, v.bitangent);
            vertices.set(i, v2);
        }
    }

    public void updateVBO() {
        numVertices = vertices.size();
        Vertex.fillVBO(vbo, vertices);
    }
}
