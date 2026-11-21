package app.pochette.tv.ui;

import android.graphics.*;

import app.pochette.tv.core.Ambiance;

/** Flat stroke pictograms for the menu, drawn from 24-unit outlines. */
final class Icons {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF irf = new RectF();

    Icons() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Big flat pictograms of the games (0 blind test, 1 chairs, 2 lyrics, 3 pixels, 4 quiz, 5 forbidden, 6 drawing). */
    void gameIcon(Canvas c, int id, float cx, float cy, float size) {
        float k = size / 24f;
        c.save();
        c.translate(cx - 12 * k, cy - 12 * k);
        c.scale(k, k, 0, 0);
        stroke.setColor(0xE6FFFFFF);
        stroke.setStrokeWidth(1.6f);
        Path p = new Path();
        switch (id) {
            case 0: p.moveTo(5, 9); p.lineTo(5, 15); p.moveTo(9, 6); p.lineTo(9, 18); p.moveTo(13, 3); p.lineTo(13, 21); p.moveTo(17, 7); p.lineTo(17, 17); p.moveTo(21, 10); p.lineTo(21, 14); break;
            case 1: p.moveTo(6, 3); p.lineTo(6, 14); p.lineTo(18, 14); p.lineTo(18, 21); p.moveTo(6, 14); p.lineTo(6, 21); p.moveTo(6, 8); p.lineTo(14, 8); break;
            case 2: p.moveTo(4, 6); p.lineTo(20, 6); p.moveTo(4, 11); p.lineTo(16, 11); p.moveTo(4, 16); p.lineTo(20, 16); p.moveTo(4, 21); p.lineTo(11, 21); break;
            case 3: p.addRect(3, 3, 9, 9, Path.Direction.CW); p.addRect(15, 3, 21, 9, Path.Direction.CW); p.addRect(9, 9, 15, 15, Path.Direction.CW); p.addRect(3, 15, 9, 21, Path.Direction.CW); p.addRect(15, 15, 21, 21, Path.Direction.CW); break;
            case 4: p.moveTo(8, 8); p.lineTo(8, 5); p.lineTo(16, 5); p.lineTo(16, 11); p.lineTo(12, 13); p.lineTo(12, 16); p.moveTo(12, 19); p.lineTo(12, 21); break;
            case 5: p.addRect(3, 9, 21, 15, Path.Direction.CW); p.moveTo(6, 6); p.lineTo(18, 18); break;
            case 7: p.addCircle(8, 17, 3, Path.Direction.CW); p.moveTo(11, 17); p.lineTo(11, 5); p.lineTo(19, 7); p.moveTo(17, 13); p.lineTo(17, 21); p.moveTo(13, 17); p.lineTo(21, 17); break;
            default: p.moveTo(4, 20); p.lineTo(6, 14); p.lineTo(16, 4); p.lineTo(20, 8); p.lineTo(10, 18); p.lineTo(4, 20); p.moveTo(14, 6); p.lineTo(18, 10); break;
        }
        stroke.setStyle(Paint.Style.STROKE);
        c.drawPath(p, stroke);
        c.restore();
    }

    /** Small pictogram per theme: snowflake, sunrise, sun, alarm clock, record, default star, plus. */
    void cardIcon(Canvas c, int theme, float cx, float cy, float size, int color) {
        float k = size / 24f;
        c.save();
        c.translate(cx - 12 * k, cy - 12 * k);
        c.scale(k, k, 0, 0);
        stroke.setColor(color);
        stroke.setStrokeWidth(2.1f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setShader(null);
        fill.setColor((color & 0x00FFFFFF) | 0x26000000);                  // soft round badge behind the pictogram
        c.drawCircle(12, 12, 17f, fill);
        Path p = new Path();
        switch (theme) {
            case -1:
                p.moveTo(12, 5); p.lineTo(12, 19); p.moveTo(5, 12); p.lineTo(19, 12);
                break;
            case Ambiance.T_XMAS:
                p.moveTo(12, 2.5f); p.lineTo(18, 11); p.lineTo(6, 11); p.close(); p.moveTo(12, 8); p.lineTo(20, 19); p.lineTo(4, 19); p.close(); p.moveTo(12, 19); p.lineTo(12, 22.5f);
                break;
            case Ambiance.T_AUTUMN:
                p.moveTo(12, 3); p.lineTo(15, 8); p.lineTo(20, 8); p.lineTo(16.5f, 12); p.lineTo(19, 17); p.lineTo(13, 15.5f); p.lineTo(12, 21); p.lineTo(11, 15.5f); p.lineTo(5, 17); p.lineTo(7.5f, 12); p.lineTo(4, 8); p.lineTo(9, 8); p.close();
                break;
            case Ambiance.T_HALLOWEEN:
                irf.set(4, 6, 20, 22); c.drawOval(irf, stroke);
                p.moveTo(12, 6); p.lineTo(12, 3); p.moveTo(8.5f, 12); p.lineTo(10.5f, 12); p.moveTo(13.5f, 12); p.lineTo(15.5f, 12); p.moveTo(8.5f, 17); p.lineTo(12, 18.5f); p.lineTo(15.5f, 17);
                break;
            case Ambiance.T_SNOW:
                p.moveTo(12, 2); p.lineTo(12, 22); p.moveTo(3.3f, 7); p.lineTo(20.7f, 17); p.moveTo(3.3f, 17); p.lineTo(20.7f, 7);
                p.moveTo(9.5f, 3.5f); p.lineTo(12, 6); p.lineTo(14.5f, 3.5f); p.moveTo(9.5f, 20.5f); p.lineTo(12, 18); p.lineTo(14.5f, 20.5f);
                break;
            case Ambiance.T_MORNING:
                irf.set(7, 13, 17, 23); c.drawArc(irf, 180, 180, false, stroke);
                p.moveTo(12, 2); p.lineTo(12, 9); p.moveTo(4.2f, 10.2f); p.lineTo(5.6f, 11.6f); p.moveTo(18.4f, 11.6f); p.lineTo(19.8f, 10.2f);
                p.moveTo(1, 18); p.lineTo(3, 18); p.moveTo(21, 18); p.lineTo(23, 18); p.moveTo(1, 22); p.lineTo(23, 22);
                break;
            case Ambiance.T_SUMMER:
                c.drawCircle(12, 12, 5, stroke);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    p.moveTo(12 + (float) Math.cos(a) * 8.5f, 12 + (float) Math.sin(a) * 8.5f);
                    p.lineTo(12 + (float) Math.cos(a) * 11f, 12 + (float) Math.sin(a) * 11f);
                }
                break;
            case Ambiance.T_WAKE:
                c.drawCircle(12, 13, 8, stroke);
                p.moveTo(12, 9); p.lineTo(12, 13); p.lineTo(14.5f, 15); p.moveTo(5, 3); p.lineTo(2, 6); p.moveTo(22, 6); p.lineTo(19, 3);
                break;
            case Ambiance.T_RAIN:
                p.moveTo(7, 3); p.lineTo(5, 9); p.moveTo(13, 2); p.lineTo(10, 11); p.moveTo(19, 3); p.lineTo(17, 9);
                p.moveTo(9, 14); p.lineTo(7, 20); p.moveTo(16, 13); p.lineTo(14, 21);
                break;
            case Ambiance.T_PLANET:
                c.drawCircle(12, 17, 6, stroke); p.moveTo(12, 11); p.lineTo(12, 6);
                p.moveTo(12, 3); p.lineTo(12, 5); p.moveTo(10.5f, 4); p.lineTo(13.5f, 4); p.moveTo(19, 4); p.lineTo(19, 7);
                break;
            case Ambiance.T_FIRE:
                p.moveTo(12, 2); p.cubicTo(13, 6, 18, 8, 18, 14); p.cubicTo(18, 18, 15, 21, 12, 21);
                p.cubicTo(9, 21, 6, 18, 6, 14); p.cubicTo(6, 11, 8, 9, 9, 7); p.cubicTo(10, 9, 11, 9, 11.5f, 7.5f); p.cubicTo(11, 5, 11, 4, 12, 2);
                break;
            case Ambiance.T_PARTY:
                p.moveTo(12, 2); p.lineTo(14, 9); p.lineTo(21, 9); p.lineTo(15.5f, 13.5f); p.lineTo(17.5f, 21); p.lineTo(12, 16.5f); p.lineTo(6.5f, 21); p.lineTo(8.5f, 13.5f); p.lineTo(3, 9); p.lineTo(10, 9); p.close();
                break;
            case Ambiance.T_SOUL:
                c.drawCircle(12, 12, 10, stroke); c.drawCircle(12, 12, 6, stroke); c.drawCircle(12, 12, 2, stroke);
                break;
            default:
                p.moveTo(9, 18); p.lineTo(9, 5); p.lineTo(20, 3); p.lineTo(20, 16);
                c.drawCircle(6, 18, 3, stroke); c.drawCircle(17, 16, 3, stroke);
                break;
        }
        c.drawPath(p, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeJoin(Paint.Join.MITER);
        c.restore();
    }

    /** Stroke icons from 24-unit outlines. 0 play,1 speaker,2 layout,3 sliders,4 help,10 prev,11 pause,12 next. */
    void icon(Canvas c, int kind, float cx, float cy, float size, int color) {
        float k = size / 24f;
        c.save();
        c.translate(cx - 12 * k, cy - 12 * k);
        c.scale(k, k, 0, 0);
        stroke.setColor(color);
        stroke.setStrokeWidth(2f);
        Path p = new Path();
        switch (kind) {
            case 0:
                p.moveTo(7, 4); p.lineTo(20, 12); p.lineTo(7, 20); p.close();
                break;
            case 1:
                irf.set(5, 2, 19, 22); c.drawRoundRect(irf, 2, 2, stroke);
                c.drawCircle(12, 14, 4, stroke);
                fill.setColor(color); c.drawCircle(12, 6, 1.1f, fill);
                break;
            case 2:
                irf.set(3, 3, 21, 21); c.drawRoundRect(irf, 2, 2, stroke);
                p.moveTo(9, 3); p.lineTo(9, 21);
                break;
            case 3:
                float[][] ls = {{4, 21, 4, 14}, {4, 10, 4, 3}, {12, 21, 12, 12}, {12, 8, 12, 3}, {20, 21, 20, 16}, {20, 12, 20, 3},
                        {1, 14, 7, 14}, {9, 8, 15, 8}, {17, 16, 23, 16}};
                for (float[] l : ls) { p.moveTo(l[0], l[1]); p.lineTo(l[2], l[3]); }
                break;
            case 4:
                c.drawCircle(12, 12, 10, stroke);
                p.moveTo(9.1f, 9f); p.cubicTo(9.5f, 6.4f, 14.9f, 6.4f, 14.9f, 10f); p.cubicTo(14.9f, 12f, 12f, 12.4f, 12f, 13.4f);
                fill.setColor(color); c.drawCircle(12, 17, 1.1f, fill);
                break;
            case 5:
                p.moveTo(12, 3); p.lineTo(14.5f, 8); p.lineTo(20, 8.8f); p.lineTo(16, 12.7f); p.lineTo(16.9f, 18.2f);
                p.lineTo(12, 15.6f); p.lineTo(7.1f, 18.2f); p.lineTo(8, 12.7f); p.lineTo(4, 8.8f); p.lineTo(9.5f, 8); p.close();
                break;
            case 8:
                p.moveTo(12, 20.5f); p.cubicTo(4, 14.5f, 2.5f, 9.5f, 5.5f, 6); p.cubicTo(8, 3.5f, 11, 4.5f, 12, 7); p.cubicTo(13, 4.5f, 16, 3.5f, 18.5f, 6); p.cubicTo(21.5f, 9.5f, 20, 14.5f, 12, 20.5f); p.close();
                break;
            case 6:
                irf.set(3, 3, 21, 21); c.drawRect(irf, stroke);
                fill.setColor(color);
                c.drawRect(7f, 7f, 9f, 9f, fill); c.drawRect(15f, 15f, 17f, 17f, fill); c.drawRect(11f, 11f, 13f, 13f, fill);
                c.drawRect(15f, 7f, 17f, 9f, fill); c.drawRect(7f, 15f, 9f, 17f, fill);
                break;
            case 10:
                p.moveTo(19, 20); p.lineTo(9, 12); p.lineTo(19, 4); p.close(); p.moveTo(5, 19); p.lineTo(5, 5);
                break;
            case 11:
                irf.set(6, 4, 10, 20); c.drawRect(irf, stroke);
                irf.set(14, 4, 18, 20); c.drawRect(irf, stroke);
                break;
            case 12:
                p.moveTo(5, 4); p.lineTo(15, 12); p.lineTo(5, 20); p.close(); p.moveTo(19, 5); p.lineTo(19, 19);
                break;
        }
        c.drawPath(p, stroke);
        c.restore();
    }
}
