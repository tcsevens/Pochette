package app.pochette.tv.ui;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

/** Muted, looping video playing behind the ambiance screens (clips are bundled in res/raw). */
public final class VideoBg implements TextureView.SurfaceTextureListener {
    private final Context ctx;
    private final TextureView tv;
    private SurfaceTexture st;
    private Surface surface;
    private MediaPlayer mp;
    private String want;      // raw resource name, or null

    public VideoBg(Context c) {
        ctx = c;
        tv = new TextureView(c);
        tv.setSurfaceTextureListener(this);
        tv.setAlpha(0.01f);   // must stay visible, or Android never gives it a surface
    }

    public View view() { return tv; }

    public void set(String res) {
        if (res == null ? want == null : res.equals(want)) return;
        want = res;
        stop();
        if (res == null) { tv.setAlpha(0.01f); return; }
        if (st != null) start();
    }

    /** Called from onPause: frees the decoder, keeps what to play. */
    public void pause() { stop(); }

    public void resume() { if (want != null && st != null && mp == null) start(); }

    private void start() {
        stop();
        int id = ctx.getResources().getIdentifier(want, "raw", ctx.getPackageName());
        if (id == 0) return;
        try {
            AssetFileDescriptor fd = ctx.getResources().openRawResourceFd(id);
            final MediaPlayer m = new MediaPlayer();
            m.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            if (surface == null) surface = new Surface(st);
            m.setSurface(surface);
            m.setVolume(0f, 0f);
            m.setLooping(true);
            m.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer p) { if (p == mp) { tv.setAlpha(0.01f); p.start(); tv.animate().alpha(1f).setStartDelay(400).setDuration(900).start(); } }
            });
            m.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override public boolean onInfo(MediaPlayer p, int what, int extra) {
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && p == mp) tv.animate().alpha(1f).setDuration(900).start();
                    return false;
                }
            });
            m.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer p, int what, int extra) { if (p == mp) stop(); return true; }
            });
            mp = m;
            m.prepareAsync();
        } catch (Exception e) {
            stop();
        }
    }

    private void stop() {
        MediaPlayer m = mp;
        mp = null;
        if (m != null) {
            try { m.stop(); } catch (Exception ignored) { }
            try { m.release(); } catch (Exception ignored) { }
        }
        tv.animate().cancel();
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
        st = s; surface = null;
        if (want != null) start();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) {
        stop();
        if (surface != null) { surface.release(); surface = null; }
        st = null;
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture s) { }
}
