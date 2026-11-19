package android.media;
/** Sound is not played in the PC preview; the calls are accepted and ignored. */
public class MediaPlayer {
    public interface OnPreparedListener { void onPrepared(MediaPlayer p); } public interface OnCompletionListener { void onCompletion(MediaPlayer p); } public interface OnErrorListener { boolean onError(MediaPlayer p, int w, int e); }
    OnPreparedListener prep; boolean playing;
    public void setAudioAttributes(AudioAttributes a) {} public void setDataSource(String s) {} public void setLooping(boolean b) {}
    public void setOnPreparedListener(OnPreparedListener l) { prep = l; } public void setOnCompletionListener(OnCompletionListener l) {} public void setOnErrorListener(OnErrorListener l) {}
    public void prepareAsync() { if (prep != null) prep.onPrepared(this); } public void start() { playing = true; } public void pause() { playing = false; } public void stop() { playing = false; } public void release() {}
    public boolean isPlaying() { return playing; } public void setVolume(float a, float b) {}
}
