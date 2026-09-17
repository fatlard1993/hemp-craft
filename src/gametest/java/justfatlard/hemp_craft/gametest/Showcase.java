package justfatlard.hemp_craft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;

/**
 * The pictures for the readme and the mod page: a hemp field standing ready to cut, a patch of
 * wild hemp on the grass, and rope hanging down a shaft.
 *
 * <p>A fibre crop and what it makes, which is what the page is about. Run it under xvfb-run; the
 * frames land in build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			context.getInput().pressKey(options -> options.keyToggleGui);
			context.runOnClient(client -> client.options.renderDistance().set(12));
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("weather clear");
			server.runCommand("time set 1000");
			server.runCommand("gamemode spectator @a");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX();
			int y = origin.getY();
			int z = origin.getZ();

			field(server, x, y, z - 14);
			wild(server, x - 22, y, z);
			shaft(server, x + 20, y, z);
			context.waitTicks(60);

			// Away and back, so every chunk these touched is sent again: a few hundred setblocks
			// into chunks the client already has do not all reach it on their own.
			server.runCommand("tp @a %d %d %d".formatted(x + 600, y + 30, z));
			context.waitTicks(80);
			server.runCommand("tp @a %d %d %d".formatted(x, y + 10, z));
			context.waitTicks(120);

			look(server, x + 0.5, y + 1.5, z - 7.0, x, y + 1.2, z - 14.0);
			context.waitTicks(40);
			shoot(context, "field");

			look(server, x - 18.0, y + 0.3, z + 4.0, x - 22.0, y + 0.9, z);
			context.waitTicks(40);
			shoot(context, "wild");

			look(server, x + 20.5, y + 2.0, z - 11.0, x + 20.5, y + 6.0, z - 1.0);
			context.waitTicks(40);
			shoot(context, "rope");
		}
	}

	/** A bed of hemp grown lanky and gone to seed: the fibre and seed crop, ready to cut. */
	private void field(TestServerContext server, int x, int y, int z) {
		server.runCommand("fill %d %d %d %d %d %d minecraft:farmland[moisture=7]"
			.formatted(x - 5, y - 1, z - 4, x + 5, y - 1, z + 4));
		server.runCommand("setblock %d %d %d minecraft:water".formatted(x, y - 1, z));
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (dx == 0 && dz == 0) continue;
				String strain = dz < -1 ? "indica" : dz > 1 ? "sativa" : "hybrid";
				int variant = Math.floorMod(dx * 3 + dz, 4);
				server.runCommand(("setblock %d %d %d hemp-craft-justfatlard:hemp_crop"
					+ "[strain=%s,form=lanky,stage=3,buds=3,variant=%d]")
					.formatted(x + dx, y, z + dz, strain, variant));
				server.runCommand(("setblock %d %d %d hemp-craft-justfatlard:hemp_stems"
					+ "[strain=%s,variant=%d]").formatted(x + dx, y + 1, z + dz, strain, variant));
			}
		}
	}

	/** Wild hemp, which is where a crop starts: a patch of it on open grass. */
	private void wild(TestServerContext server, int x, int y, int z) {
		for (int[] spot : new int[][] {{0, 0}, {1, 2}, {-2, 1}, {2, -1}, {-1, -2}, {3, 1}, {-3, 0}}) {
			server.runCommand(("setblock %d %d %d hemp-craft-justfatlard:wild_hemp"
				+ "[strain=indica,variant=%d]")
				.formatted(x + spot[0], y, z + spot[1], Math.floorMod(spot[0] + spot[1], 4)));
		}
	}

	/**
	 * What the fibre is for: rope down the face of a bank, which is the way back up.
	 *
	 * <p>Down a face rather than down a shaft. A shaft reads as a hole in the grass from anywhere
	 * a camera can stand; a rope on an open face is a rope.
	 */
	private void shaft(TestServerContext server, int x, int y, int z) {
		server.runCommand("fill %d %d %d %d %d %d minecraft:stone"
			.formatted(x - 6, y, z, x + 6, y + 10, z + 8));
		server.runCommand("fill %d %d %d %d %d %d minecraft:grass_block"
			.formatted(x - 6, y + 11, z, x + 6, y + 11, z + 8));
		// A beam over the lip for the rope to hang from, and the rope down the face.
		server.runCommand("setblock %d %d %d minecraft:oak_slab[type=bottom]"
			.formatted(x, y + 11, z - 1));
		for (int down = 0; down < 9; down++) {
			server.runCommand("setblock %d %d %d hemp-craft-justfatlard:rope"
				.formatted(x, y + 10 - down, z - 1));
		}
		server.runCommand("setblock %d %d %d minecraft:lantern".formatted(x + 2, y + 11, z - 1));
	}

	/** Stand the camera at one place and point it at another; the y is the feet. */
	private void look(TestServerContext server, double x, double y, double z,
			double atX, double atY, double atZ) {
		double dx = atX - x;
		double dy = atY - (y + 1.62);
		double dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
