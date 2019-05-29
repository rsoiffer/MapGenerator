package game;

import graphics.opengl.BufferObject;
import graphics.opengl.VertexArrayObject;
import java.util.List;
import java.util.stream.IntStream;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import util.math.Vec2d;
import util.math.Vec3d;

public interface Vertex {

    public float[] data();

    public int size();

    public static VertexArrayObject createVAO(List<? extends Vertex> vertices, int[] attribs) {
        return createVAO(createVBO(vertices), attribs);
    }

    public static VertexArrayObject createVAO(BufferObject vbo, int[] attribs) {
        return VertexArrayObject.createVAO(() -> {
            vbo.bind();
            int pos = 0, size = IntStream.of(attribs).sum();
            for (int i = 0; i < attribs.length; i++) {
                glVertexAttribPointer(i, attribs[i], GL_FLOAT, false, size * 4, pos);
                glEnableVertexAttribArray(i);
                pos += attribs[i] * 4;
            }
        });
    }

    public static BufferObject createVBO(List<? extends Vertex> vertices) {
        BufferObject vbo = new BufferObject(GL_ARRAY_BUFFER);
        fillVBO(vbo, vertices);
        return vbo;
    }

    public static void fillVBO(BufferObject vbo, List<? extends Vertex> vertices) {
        int totalSize = vertices.stream().mapToInt(Vertex::size).sum();
        float[] data = new float[totalSize];
        int pos = 0;
        for (int i = 0; i < vertices.size(); i++) {
            System.arraycopy(vertices.get(i).data(), 0, data, pos, vertices.get(i).size());
            pos += vertices.get(i).size();
        }
        vbo.putData(data, GL_STREAM_DRAW);
    }

    public class VertexColor implements Vertex {

        public final Vec3d position;
        public final Vec3d color;
        public final Vec3d normal;

        public VertexColor(Vec3d position, Vec3d color, Vec3d normal) {
            this.position = position;
            this.color = color;
            this.normal = normal;
        }

        @Override
        public float[] data() {
            return new float[]{
                (float) position.x, (float) position.y, (float) position.z,
                (float) color.x, (float) color.y, (float) color.z,
                (float) normal.x, (float) normal.y, (float) normal.z
            };
        }

        @Override
        public int size() {
            return 9;
        }

        @Override
        public String toString() {
            return "VertexColor{" + "position=" + position + ", color=" + color + ", normal=" + normal + '}';
        }
    }

    public class VertexPBR implements Vertex {

        public final Vec3d position;
        public final Vec2d texCoord;
        public final Vec3d normal;
        public final Vec3d tangent;
        public final Vec3d bitangent;

        public VertexPBR(Vec3d position, Vec2d texCoord, Vec3d normal, Vec3d tangent, Vec3d bitangent) {
            this.position = position;
            this.texCoord = texCoord;
            this.normal = normal;
            this.tangent = tangent;
            this.bitangent = bitangent;
        }

        @Override
        public float[] data() {
            return new float[]{
                (float) position.x, (float) position.y, (float) position.z,
                (float) texCoord.x, (float) texCoord.y,
                (float) normal.x, (float) normal.y, (float) normal.z,
                (float) tangent.x, (float) tangent.y, (float) tangent.z,
                (float) bitangent.x, (float) bitangent.y, (float) bitangent.z
            };
        }

        @Override
        public int size() {
            return 14;
        }

        @Override
        public String toString() {
            return "VertexPBR{" + "position=" + position + ", texCoord=" + texCoord + ", normal=" + normal + ", tangent=" + tangent + ", bitangent=" + bitangent + '}';
        }
    }
}
