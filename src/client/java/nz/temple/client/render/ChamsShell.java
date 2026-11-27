package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ChamsShell {
	private ChamsShell() {
	}
	public static void draw(PoseStack.Pose pose, ModelPart root, VertexConsumer consumer, float grow, int light, int overlay, int color) {
		PoseStack stack = new PoseStack();
		stack.last().set(pose);
		float g = grow * ModelPart.Vertex.SCALE_FACTOR;
		Vector3f scratch = new Vector3f();
		root.visit(stack, (partPose, path, index, cube) -> {
			Matrix4f matrix = partPose.pose();
			float cx = (cube.minX + cube.maxX) * 0.5f;
			float cy = (cube.minY + cube.maxY) * 0.5f;
			float cz = (cube.minZ + cube.maxZ) * 0.5f;
			for (ModelPart.Polygon polygon : cube.polygons) {
				Vector3f normal = partPose.transformNormal(polygon.normal(), scratch);
				float nx = normal.x();
				float ny = normal.y();
				float nz = normal.z();
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					float x = (vertex.x() + Math.copySign(g, vertex.x() - cx)) / ModelPart.Vertex.SCALE_FACTOR;
					float y = (vertex.y() + Math.copySign(g, vertex.y() - cy)) / ModelPart.Vertex.SCALE_FACTOR;
					float z = (vertex.z() + Math.copySign(g, vertex.z() - cz)) / ModelPart.Vertex.SCALE_FACTOR;
					Vector3f pos = matrix.transformPosition(x, y, z, new Vector3f());
					consumer.addVertex(pos.x(), pos.y(), pos.z(), color, vertex.u(), vertex.v(), overlay, light, nx, ny, nz);
				}
			}
		});
	}
}
