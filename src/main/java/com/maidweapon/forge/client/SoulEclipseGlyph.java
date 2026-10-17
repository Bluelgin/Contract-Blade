package com.maidweapon.forge.client;

import com.maidweapon.forge.system.akatsuki.soul.SoulEclipseGeometry;
import com.maidweapon.forge.system.akatsuki.soul.SoulEclipseGeometry.Point;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Low-cost batched position/color geometry. Every drawing is a quad, no texture,
 * persistent particle, shader framebuffer, model lookup or server-side logic.
 */
final class SoulEclipseGlyph {
  private static final int ORBIT = 0xAE7883;
  private static final int RED = 0xA51131;
  private static final int RED_LIGHT = 0xF3506C;
  private static final int STAR = 0xFFF4E9;

  static void idle(VertexConsumer out, Matrix4f matrix, double tick, int quality,
      int progress) {
    double opacity = .48 + .10 * Math.sin(tick * .09);
    if (progress < 0) opacity = .17;
    orbit(out, matrix, tick, quality == 0 ? 16 : quality == 1 ? 28 : 40, (float) opacity);
    // The original three satellites are solely an atmospheric constellation.
    // None of them are separate targets and they stay visible through all five hits.
    for (int i = 0; i < SoulEclipseGeometry.STARS; i++) {
      var point = SoulEclipseGeometry.star(i, tick);
      star(out, matrix, point, quality == 0 ? .07 : .09, STAR,
          progress < 0 ? .2f : .88f);
    }
    moon(out, matrix, tick, progress < 0 ? .2f : .85f, progress >= 5);
    // Each authenticated sword contact leaves a short additional crack on the moon.
    // These lines are visual feedback only; the five-hit ledger stays server-side.
    for (int i = 0; i < Math.max(0, Math.min(5, progress)); i++) {
      double x = -.19 + i * .095;
      ribbon(out, matrix, new Point(x - .04, -.17 + i * .025, .14),
          new Point(x + .08, .09 + i * .015, .14), .014,
          i == 4 ? STAR : RED_LIGHT, .9f);
    }
  }

  static void stage(VertexConsumer out, Matrix4f matrix, double tick, int stage,
      int stages, double age, boolean hit, int quality) {
    float fade = SoulEclipseGeometry.fade(age, 5);
    if (fade <= 0) return;
    boolean last = stage == stages - 1;
    float alpha = fade * (hit ? 1 : .3f);
    orbit(out, matrix, tick, quality == 0 ? 16 : quality == 1 ? 28 : 40, alpha * (last ? .25f : .65f));
    if (!last) {
      int consumed = SoulEclipseGeometry.brokenStar(stage, stages);
      for (int i = 0; i < SoulEclipseGeometry.STARS; i++) {
        var origin = SoulEclipseGeometry.star(i, tick);
        boolean broken = i == consumed;
        double shift = broken ? Math.min(1, age / 3) : 0;
        Point destination = switch (consumed) {
          case 0 -> new Point(origin.x() * 1.6, origin.y() * 1.6, .03);
          case 1 -> new Point(0, 0, .04); // The second star is dragged inward.
          default -> SoulEclipseGeometry.orbit(5.46, tick); // A star falls into the track gap.
        };
        Point point = origin.lerp(destination, shift);
        float brightness = alpha * (broken ? 1 - (float) shift * .7f : .35f);
        star(out, matrix, point, broken ? .16 : .075, broken ? STAR : RED_LIGHT, brightness);
        if (broken && consumed == 0 && quality > 0) {
          for (int fleck = 0; fleck < 3; fleck++) {
            double angle = fleck * 2 * Math.PI / 3 + .4;
            star(out, matrix, new Point(point.x() + Math.cos(angle) * shift * .18,
                point.y() + Math.sin(angle) * shift * .18, .09), .035,
                RED_LIGHT, alpha * (1 - (float) shift));
          }
        }
      }
      moon(out, matrix, tick, alpha * .8f, false);
      Point origin = SoulEclipseGeometry.star(consumed, tick);
      // Only the second pulse drags material back toward the moon; the third breaks orbit.
      if (consumed == 1)
        ribbon(out, matrix, origin, new Point(0, 0, .08), .015, RED_LIGHT, alpha * .8f);
      else if (consumed == 2)
        ribbon(out, matrix, origin, SoulEclipseGeometry.orbit(5.46, tick),
            .018, STAR, alpha * .65f);
    } else {
      // The last stage is the moon cleaving, not another explosion of random stars.
      moon(out, matrix, tick, alpha * .8f, true);
      ribbon(out, matrix, new Point(-.57 - age * .07, -.28, .05),
          new Point(.57 + age * .07, .28, .05), .032, STAR, alpha);
      for (int i = 0; i < (quality == 0 ? 4 : 8); i++) {
        double a = i * 2 * Math.PI / (quality == 0 ? 4 : 8) + .2;
        double distance = .25 + age * .12;
        var p = new Point(Math.cos(a) * distance, Math.sin(a) * distance, 0);
        star(out, matrix, p, .05 + age * .018, i % 2 == 0 ? STAR : RED_LIGHT, alpha * .55f);
      }
    }
  }

  private static void orbit(VertexConsumer out, Matrix4f matrix,
      double tick, int steps, float opacity) {
    for (int i = 0; i < steps; i++) {
      double angle = i * 2 * Math.PI / steps;
      double next = (i + 1) * 2 * Math.PI / steps;
      if (!SoulEclipseGeometry.onTrack(angle) || !SoulEclipseGeometry.onTrack(next)) continue;
      Point a = SoulEclipseGeometry.orbit(angle, tick);
      Point b = SoulEclipseGeometry.orbit(next, tick);
      ribbon(out, matrix, a, b, .012, ORBIT, opacity);
      if (i % 4 == 0) star(out, matrix, a, .022, RED_LIGHT, opacity * .7f);
    }
  }

  private static void moon(VertexConsumer out, Matrix4f m, double tick, float alpha, boolean cracked) {
    // Intentionally interrupted curved silhouette with a diagonal incision.
    final int parts = 20;
    for (int i = 0; i < parts; i++) {
      double a = -2.6 + i * 5.2 / parts;
      double b = -2.6 + (i + 1) * 5.2 / parts;
      if (cracked && i >= 9 && i <= 11) continue;
      Point p = new Point(Math.cos(a) * .31, Math.sin(a) * .31, .1);
      Point q = new Point(Math.cos(b) * .31, Math.sin(b) * .31, .1);
      ribbon(out, m, p, q, .033, RED, alpha);
      ribbon(out, m, p, q, .012, RED_LIGHT, alpha * .9f);
    }
    ribbon(out, m, new Point(-.23, -.14, .12), new Point(.23, .14, .12),
        cracked ? .045 : .016, cracked ? STAR : RED_LIGHT, alpha);
    star(out, m, new Point(0, 0, .09), .055, RED_LIGHT, alpha * .8f);
  }

  private static void star(VertexConsumer out, Matrix4f m,
      Point at, double radius, int color, float alpha) {
    if (alpha <= .001f) return;
    Point top = new Point(at.x(), at.y() + radius * 1.55, at.z());
    Point bottom = new Point(at.x(), at.y() - radius * 1.55, at.z());
    Point left = new Point(at.x() - radius * .8, at.y(), at.z());
    Point right = new Point(at.x() + radius * .8, at.y(), at.z());
    quad(out, m, top, right, bottom, left, color, alpha);
  }

  private static void ribbon(VertexConsumer out, Matrix4f m, Point a, Point b,
      double half, int color, float alpha) {
    if (alpha <= .001f) return;
    double dx = b.x() - a.x(), dy = b.y() - a.y();
    double length = Math.hypot(dx, dy);
    if (length < 1e-8) return;
    double nx = -dy / length * half, ny = dx / length * half;
    quad(out, m,
        new Point(a.x() + nx, a.y() + ny, a.z()),
        new Point(b.x() + nx, b.y() + ny, b.z()),
        new Point(b.x() - nx, b.y() - ny, b.z()),
        new Point(a.x() - nx, a.y() - ny, a.z()), color, alpha);
  }

  private static void quad(VertexConsumer out, Matrix4f m, Point a, Point b, Point c, Point d,
      int rgb, float alpha) {
    vertex(out, m, a, rgb, alpha);
    vertex(out, m, b, rgb, alpha);
    vertex(out, m, c, rgb, alpha);
    vertex(out, m, d, rgb, alpha);
  }

  private static void vertex(VertexConsumer out, Matrix4f m, Point p, int color, float opacity) {
    out.vertex(m, (float) p.x(), (float) p.y(), (float) p.z())
        .color(color >> 16 & 255, color >> 8 & 255, color & 255,
            Mth.clamp((int) (opacity * 255), 0, 255)).endVertex();
  }

  private SoulEclipseGlyph() {}
}
