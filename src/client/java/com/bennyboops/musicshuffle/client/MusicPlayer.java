package com.bennyboops.musicshuffle.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicPlayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MusicShuffleClient.MOD_ID);
    private static final String[] SUPPORTED_EXTENSIONS = { ".wav", ".ogg" };
    private static final int BUFFER_SIZE = 4096 * 8;

    private final File musicFolder;

    private Thread playbackThread;
    private final AtomicBoolean running            = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested      = new AtomicBoolean(false);
    private final AtomicBoolean paused             = new AtomicBoolean(false);
    private final AtomicBoolean rewindRequested    = new AtomicBoolean(false);
    private final AtomicBoolean skipRequested      = new AtomicBoolean(false);
    private final AtomicBoolean reshuffleRequested = new AtomicBoolean(false);

    private volatile boolean queueLocked = false;
    private volatile boolean queueCleared = false;

    private volatile float masterVolume = 1.0f;
    private volatile SourceDataLine currentLine;
    private volatile String currentTrackName = "";
    private volatile String lastPlayedPath = "";
    private volatile File currentTrackFile = null;
    private volatile File jumpToTrack = null;

    private volatile long currentFrame = 0;
    private volatile long totalFrames  = 0;

    private volatile List<File> pendingReshuffledQueue = null;

    private final ConcurrentLinkedQueue<File> queueNextTracks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<int[]> reorderRequests = new ConcurrentLinkedQueue<>();

    private final Set<String> playedThisCycle =
            Collections.synchronizedSet(new HashSet<>());

    private volatile List<String> queueSnapshot = Collections.emptyList();
    private volatile LoopMode loopMode = LoopMode.OFF;

    private List<File> savedQueue = null;
    private int savedIndex = 0;

    private final List<File> pendingAlbumQueue = new ArrayList<>();
    private final Set<String> blacklist = new HashSet<>();

    private java.util.function.Supplier<List<File>> trackSupplier = null;


    public MusicPlayer(File musicFolder) {
        this.musicFolder = musicFolder;
    }

    public enum LoopMode {
        OFF,
        QUEUE,
        TRACK
    }

    public void start() {
        if (running.get()) return;

        queueLocked = false;
        queueCleared = false;

        List<File> tracks = scanTracks();
        if (tracks.isEmpty()) {
            LOGGER.info("[MusicShuffle] No supported audio files found in: {}", musicFolder.getAbsolutePath());
            return;
        }

        LOGGER.info("[MusicShuffle] Starting shuffle playback with {} track(s).", tracks.size());
        stopRequested.set(false);
        paused.set(false);
        skipRequested.set(false);
        reshuffleRequested.set(false);
        running.set(true);
        savedQueue = null;

        playbackThread = new Thread(() -> playbackLoop(tracks), "MusicShuffle-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    public void resume() {
        if (running.get()) return;

        queueLocked = false;
        queueCleared = false;

        if (savedQueue == null || savedQueue.isEmpty()) {
            LOGGER.info("[MusicShuffle] No saved queue — falling back to fresh start.");
            start();
            return;
        }

        LOGGER.info("[MusicShuffle] Resuming playback from saved queue (index {}/{}).",
                savedIndex, savedQueue.size());
        stopRequested.set(false);
        paused.set(false);
        skipRequested.set(false);
        reshuffleRequested.set(false);
        running.set(true);

        List<File> queueCopy = new ArrayList<>(savedQueue);
        int indexCopy = savedIndex;
        savedQueue = null;

        playbackThread = new Thread(() -> playbackLoop(queueCopy, indexCopy), "MusicShuffle-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    public void stop() {
        if (!running.get()) return;

        LOGGER.info("[MusicShuffle] Stopping playback.");
        stopRequested.set(true);
        paused.set(false);
        interruptCurrentLine();

        if (playbackThread != null) playbackThread.interrupt();
        running.set(false);
    }

    public void togglePause() {
        if (!running.get()) return;

        boolean nowPaused = !paused.get();
        paused.set(nowPaused);
        if (!nowPaused) {
            synchronized (paused) { paused.notifyAll(); }
        }
        LOGGER.info("[MusicShuffle] Playback {}.", nowPaused ? "paused" : "resumed");
    }

    public void rewind() {
        if (!running.get()) return;

        LOGGER.info("[MusicShuffle] Rewinding.");
        rewindRequested.set(true);
        paused.set(false);
        synchronized (paused) { paused.notifyAll(); }
        interruptCurrentLine();
    }

    public void skip() {
        if (!running.get()) return;
        LOGGER.info("[MusicShuffle] Skipping track.");
        skipRequested.set(true);
        paused.set(false);
        synchronized (paused) { paused.notifyAll(); }
        interruptCurrentLine();
    }

    public void clearQueue() {
        if (!running.get()) return;

        LOGGER.info("[MusicShuffle] Clearing queue.");

        queueNextTracks.clear();
        reorderRequests.clear();
        synchronized (this) {
            pendingAlbumQueue.clear();
        }

        queueSnapshot = Collections.emptyList();

        pendingReshuffledQueue = null;
        jumpToTrack = null;
        savedQueue = null;
        playedThisCycle.clear();

        queueLocked = true;
        queueCleared = true;
    }

    public void queueNext(File track) {
        if (!running.get()) return;

        LOGGER.info("[MusicShuffle] Queue-next requested: {}", track.getName());
        queueNextTracks.offer(track);

        Set<String> pendingNames = new java.util.LinkedHashSet<>();
        for (File f : queueNextTracks) pendingNames.add(stripExtension(f.getName()));

        List<String> current = queueSnapshot;
        List<String> updated = new ArrayList<>(current.size() + 1);
        updated.addAll(pendingNames);
        for (String s : current) {
            if (!pendingNames.contains(s)) updated.add(s);
        }
        queueSnapshot = Collections.unmodifiableList(updated);
    }

    public void reorderQueue(int fromSnapshotIndex, int toSnapshotIndex) {
        if (!running.get()) return;

        reorderRequests.offer(new int[]{ fromSnapshotIndex, toSnapshotIndex });

        List<String> current = new ArrayList<>(queueSnapshot);
        if (fromSnapshotIndex >= 0 && fromSnapshotIndex < current.size()
                && toSnapshotIndex >= 0 && toSnapshotIndex < current.size()) {
            String item = current.remove(fromSnapshotIndex);
            current.add(toSnapshotIndex, item);
            queueSnapshot = Collections.unmodifiableList(current);
        }
    }

    public void reshuffle() {
        queueLocked = false;
        queueCleared = false;

        if (!running.get()) return;

        LOGGER.info("[MusicShuffle] Reshuffling queue.");

        List<File> freshTracks = getNextQueueTracks();
        if (freshTracks.isEmpty()) return;

        String currentPath = lastPlayedPath;

        List<File> reshuffled = new ArrayList<>();
        for (File f : freshTracks) {
            if (!f.getAbsolutePath().equals(currentPath)) {
                reshuffled.add(f);
            }
        }

        shuffleAvoidingLastPlayed(reshuffled);

        pendingReshuffledQueue = reshuffled;

        queueNextTracks.clear();
        reorderRequests.clear();

        List<String> snap = new ArrayList<>();
        for (File f : reshuffled) {
            snap.add(stripExtension(f.getName()));
        }
        queueSnapshot = Collections.unmodifiableList(snap);

        reshuffleRequested.set(true);

        paused.set(false);
        synchronized (paused) {
            paused.notifyAll();
        }
    }

    public void reshuffleForDimension(List<File> dimensionTracks, boolean skipCurrent) {
        if (!running.get()) return;

        LOGGER.info("[MusicShuffle] Reshuffling for dimension ({} track(s), skip={}).",
                dimensionTracks.size(), skipCurrent);

        queueLocked  = false;
        queueCleared = false;

        List<File> reshuffled = new ArrayList<>(dimensionTracks);
        shuffleAvoidingLastPlayed(reshuffled);

        pendingReshuffledQueue = reshuffled;

        queueNextTracks.clear();
        reorderRequests.clear();

        List<String> snap = new ArrayList<>();
        for (File f : reshuffled) {
            snap.add(stripExtension(f.getName()));
        }
        queueSnapshot = Collections.unmodifiableList(snap);

        reshuffleRequested.set(true);

        paused.set(false);
        synchronized (paused) { paused.notifyAll(); }

        if (skipCurrent) {
            skipRequested.set(true);
            interruptCurrentLine();
        }
    }

    public LoopMode getLoopMode() {
        return loopMode;
    }

    public void cycleLoopMode() {
        switch (loopMode) {
            case OFF   -> loopMode = LoopMode.QUEUE;
            case QUEUE -> loopMode = LoopMode.TRACK;
            case TRACK -> loopMode = LoopMode.OFF;
        }

        LOGGER.info("[MusicShuffle] Loop mode: {}", loopMode);
    }

    public void reshuffleAlbum(String albumName) {
        if (!running.get()) return;
        LOGGER.info("[MusicShuffle] Reshuffling album: {}", albumName);

        List<File> albumTracks = new ArrayList<>();
        File[] topLevel = musicFolder.listFiles();
        if (topLevel != null) {
            if ("Unsorted".equals(albumName)) {
                for (File f : topLevel) {
                    if (f.isFile() && isSupportedExtension(f.getName()) && !blacklist.contains(f.getName())) {
                        albumTracks.add(f);
                    }
                }
            } else {
                for (File f : topLevel) {
                    if (f.isDirectory() && f.getName().equals(albumName)) {
                        File[] contents = f.listFiles();
                        if (contents != null) {
                            for (File track : contents) {
                                if (track.isFile() && isSupportedExtension(track.getName())
                                        && !blacklist.contains(track.getName())) {
                                    albumTracks.add(track);
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }

        if (albumTracks.isEmpty()) return;
        shuffleAvoidingLastPlayed(albumTracks);

        synchronized (this) {
            pendingAlbumQueue.clear();
            pendingAlbumQueue.addAll(albumTracks);
        }

        skipRequested.set(true);
        paused.set(false);
        synchronized (paused) { paused.notifyAll(); }
        interruptCurrentLine();
    }

    public void playTrackNow(File track) {
        if (!running.get()) return;

        LOGGER.info("[MusicShuffle] Jump-to-track requested: {}", track.getName());
        jumpToTrack = track;
        skipRequested.set(true);
        paused.set(false);
        synchronized (paused) { paused.notifyAll(); }
        interruptCurrentLine();
    }

    public void setBlacklist(Set<String> blacklist) {
        this.blacklist.clear();
        this.blacklist.addAll(blacklist);
    }

    public void setTrackSupplier(java.util.function.Supplier<List<File>> supplier) {
        this.trackSupplier = supplier;
    }

    public Set<String> getBlacklist() {
        return Collections.unmodifiableSet(blacklist);
    }

    public boolean isRunning() { return running.get(); }
    public boolean isPaused()  { return paused.get();  }

    public String getCurrentTrackName() { return currentTrackName; }

    public List<String> getQueueSnapshot() { return queueSnapshot; }

    public long getCurrentFrame() { return currentFrame; }

    public long getTotalFrames()  { return totalFrames; }

    public void setVolume(float volume) {
        masterVolume = Math.max(0f, Math.min(1f, volume));
        applyVolumeToLine();
    }

    private void shuffleAvoidingLastPlayed(List<File> list) {
        Collections.shuffle(list);
        if (list.size() < 2 || lastPlayedPath.isEmpty()) return;
        if (list.get(0).getAbsolutePath().equals(lastPlayedPath)) {
            int swapIdx = 1 + (int)(Math.random() * (list.size() - 1));
            Collections.swap(list, 0, swapIdx);
        }
    }

    private void playbackLoop(List<File> initialTracks) {
        List<File> queue = new ArrayList<>(initialTracks);
        shuffleAvoidingLastPlayed(queue);
        playedThisCycle.clear();
        playbackLoop(queue, 0);
    }

    private void playbackLoop(List<File> queue, int startIndex) {
        int index = startIndex;
        publishQueueSnapshot(queue, index);

        while (!stopRequested.get()) {

            if (queueCleared) {
                if (!queue.isEmpty()) {
                    queue = new ArrayList<>();
                    index = 0;
                    publishQueueSnapshot(queue, index);
                }
                queueCleared = false;
            }

            if (reshuffleRequested.get()) {
                reshuffleRequested.set(false);

                List<File> preservedNext = new ArrayList<>();
                File peeked;
                while ((peeked = queueNextTracks.poll()) != null) preservedNext.add(peeked);

                reorderRequests.clear();

                if (pendingReshuffledQueue != null && !pendingReshuffledQueue.isEmpty()) {
                    queue = new ArrayList<>(pendingReshuffledQueue);
                    pendingReshuffledQueue = null;
                    index = 0;
                    LOGGER.info("[MusicShuffle] Queue reshuffled with {} upcoming track(s).", queue.size());
                }

                for (int i = preservedNext.size() - 1; i >= 0; i--) {
                    queueNextTracks.offer(preservedNext.get(i));
                }

                publishQueueSnapshot(queue, index);
            }

            if (!queueNextTracks.isEmpty()) {
                int insertPos = index;
                File f;
                while ((f = queueNextTracks.poll()) != null) {
                    for (int i = insertPos; i < queue.size(); i++) {
                        if (queue.get(i).equals(f)) {
                            queue.remove(i);
                            break;
                        }
                    }
                    if (insertPos > queue.size()) insertPos = queue.size();
                    queue.add(insertPos, f);
                    insertPos++;
                }
                publishQueueSnapshot(queue, index);
            }

            {
                int[] req;
                boolean didReorder = false;
                while ((req = reorderRequests.poll()) != null) {
                    int from = index + req[0];
                    int to   = index + req[1];
                    if (from >= index && from < queue.size()
                            && to >= index && to < queue.size()) {
                        File track = queue.remove(from);
                        queue.add(to, track);
                        didReorder = true;
                    }
                }
                if (didReorder) publishQueueSnapshot(queue, index);
            }

            File next = null;
            boolean nextFromQueue = false;
            synchronized (this) {
                if (!pendingAlbumQueue.isEmpty()) {
                    next = pendingAlbumQueue.remove(0);
                    nextFromQueue = true;
                    skipRequested.set(false);
                    publishQueueSnapshot(pendingAlbumQueue, 0);
                }
            }

            if (next == null && jumpToTrack != null) {
                next = jumpToTrack;
                jumpToTrack = null;
                nextFromQueue = true;
                skipRequested.set(false);

                if (!playedThisCycle.contains(next.getAbsolutePath())) {
                    queue.remove(next);
                    if (index > queue.size()) index = queue.size();
                }
                queue.add(index, next);
                index++;
                publishQueueSnapshot(queue, index);
            }

            if (next == null) {
                skipRequested.set(false);

                if (rewindRequested.get()) {
                    rewindRequested.set(false);
                    index = Math.max(0, index - 2);
                    publishQueueSnapshot(queue, index);
                }

                if (index >= queue.size()) {

                    if (loopMode == LoopMode.QUEUE && !queue.isEmpty()) {
                        index = 0;
                        LOGGER.info("[MusicShuffle] Looping queue.");
                        publishQueueSnapshot(queue, index);
                        continue;
                    }

                    if (queueLocked && loopMode == LoopMode.OFF && skipRequested.get()) {
                        List<File> freshTracks = getNextQueueTracks();
                        if (!freshTracks.isEmpty()) {
                            queue = new ArrayList<>(freshTracks);
                            shuffleAvoidingLastPlayed(queue);

                            queueLocked = false;
                            queueCleared = false;
                            skipRequested.set(false);

                            index = 0;
                            LOGGER.info("[MusicShuffle] Rebuilt queue after skip.");
                            publishQueueSnapshot(queue, index);
                            continue;
                        }
                    }

                    if (queueLocked) {
                        if (loopMode == LoopMode.TRACK && currentTrackFile != null) {
                            queue = new ArrayList<>();
                            queue.add(currentTrackFile);
                            index = 0;
                            skipRequested.set(false);
                            publishQueueSnapshot(queue, index);
                            continue;
                        }

                        if (!queue.isEmpty()) {
                            index = Math.max(0, queue.size() - 1);
                            publishQueueSnapshot(queue, index);
                            continue;
                        }

                        trySleep(100);
                        continue;
                    }

                    List<File> freshTracks = getNextQueueTracks();
                    if (freshTracks.isEmpty()) {
                        LOGGER.info("[MusicShuffle] Music folder empty — stopping.");
                        break;
                    }

                    queue = new ArrayList<>(freshTracks);
                    shuffleAvoidingLastPlayed(queue);

                    index = 0;
                    queueCleared = false;

                    LOGGER.info("[MusicShuffle] Queue rebuilt (normal flow).");
                    publishQueueSnapshot(queue, index);
                }

                if (next == null) {
                    next = queue.get(index++);
                    nextFromQueue = true;
                    publishQueueSnapshot(queue, index);
                }
            }

            playedThisCycle.add(next.getAbsolutePath());
            lastPlayedPath = next.getAbsolutePath();
            currentTrackFile = next;

            savedQueue = new ArrayList<>(queue);
            savedIndex = index;

            Thread.interrupted();

            playTrack(next);

            if (loopMode == LoopMode.TRACK
                    && !stopRequested.get()
                    && !skipRequested.get()
                    && jumpToTrack == null
                    && nextFromQueue) {
                index--;
            }

            if (!stopRequested.get() && jumpToTrack == null) {
                long delayMin = MusicShuffleClient.trackDelayMinMs;
                long delayMax = MusicShuffleClient.trackDelayMaxMs;
                if (delayMax < delayMin) delayMax = delayMin;
                long extra = delayMin == delayMax ? delayMin
                        : delayMin + (long)(Math.random() * (delayMax - delayMin + 1));
                trySleep(500 + extra);
            }
        }

        running.set(false);
        queueSnapshot = Collections.emptyList();
        LOGGER.info("[MusicShuffle] Playback loop ended.");
    }

    private void publishQueueSnapshot(List<File> queue, int fromIndex) {
        List<String> snap = new ArrayList<>();
        int start = Math.max(0, Math.min(fromIndex, queue.size()));
        for (int i = start; i < queue.size(); i++) {
            snap.add(stripExtension(queue.get(i).getName()));
        }
        queueSnapshot = Collections.unmodifiableList(snap);
    }

    private void playTrack(File file) {
        if (stopRequested.get()) return;

        waitWhilePaused();
        if (stopRequested.get() || skipRequested.get()) return;

        currentTrackName = stripExtension(file.getName());
        LOGGER.info("[MusicShuffle] Now playing: {}", file.getName());

        try (AudioInputStream rawStream = AudioSystem.getAudioInputStream(file)) {

            AudioFormat sourceFormat = rawStream.getFormat();
            AudioFormat pcmFormat    = toPCM(sourceFormat);

            currentFrame = 0;
            long reportedFrames = rawStream.getFrameLength();
            if (reportedFrames > 0) {
                totalFrames = reportedFrames;
            } else {
                totalFrames = countPcmFrames(file, pcmFormat);
            }

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, rawStream)) {

                DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFormat);
                if (!AudioSystem.isLineSupported(info)) {
                    LOGGER.warn("[MusicShuffle] Audio line not supported for: {}", file.getName());
                    return;
                }

                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                    currentLine = line;
                    line.open(pcmFormat, BUFFER_SIZE);
                    applyVolumeToLine();
                    line.start();

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    int frameSize = pcmFormat.getFrameSize();

                    while (!stopRequested.get() && !skipRequested.get() && jumpToTrack == null) {

                        if (paused.get()) {
                            line.stop();
                            waitWhilePaused();
                            if (stopRequested.get() || skipRequested.get() || jumpToTrack != null) break;
                            line.start();
                        }

                        bytesRead = pcmStream.read(buffer, 0, buffer.length);
                        if (bytesRead == -1) break;

                        applyVolumeToLine();
                        line.write(buffer, 0, bytesRead);
                        currentFrame += bytesRead / frameSize;
                    }

                    if (!stopRequested.get() && !skipRequested.get() && jumpToTrack == null) {
                        line.drain();
                    }
                    line.stop();
                }
            }

        } catch (UnsupportedAudioFileException e) {
            LOGGER.error("[MusicShuffle] BAD OGG FILE: {}", file.getAbsolutePath());
            LOGGER.error("Reason: {}", e.getMessage());
        } catch (IOException e) {
            LOGGER.error("[MusicShuffle] IO error reading '{}': {}", file.getName(), e.getMessage());
        } catch (LineUnavailableException e) {
            LOGGER.error("[MusicShuffle] Audio line unavailable for '{}': {}", file.getName(), e.getMessage());
        } finally {
            currentLine  = null;
            currentFrame = 0;
            totalFrames  = 0;
        }
    }

    private void applyVolumeToLine() {
        SourceDataLine line = currentLine;
        if (line == null || !line.isOpen()) return;

        if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = volumeToDecibels(masterVolume, gainControl.getMinimum(), gainControl.getMaximum());
            gainControl.setValue(dB);
        } else if (line.isControlSupported(FloatControl.Type.VOLUME)) {
            FloatControl volControl = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
            volControl.setValue(masterVolume * volControl.getMaximum());
        }
    }

    private static float volumeToDecibels(float linear, float minDb, float maxDb) {
        if (linear <= 0f) return minDb;
        float dB = (float) (20.0 * Math.log10(linear));
        return Math.max(minDb, Math.min(maxDb, dB));
    }

    private void waitWhilePaused() {
        synchronized (paused) {
            while (paused.get() && !stopRequested.get() && !skipRequested.get()) {
                try {
                    paused.wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void trySleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private void interruptCurrentLine() {
        SourceDataLine line = currentLine;
        if (line != null && line.isOpen()) {
            line.stop();
            line.close();
        }
        if (playbackThread != null) playbackThread.interrupt();
    }

    private List<File> getNextQueueTracks() {
        if (trackSupplier != null) {
            List<File> supplied = trackSupplier.get();
            if (supplied != null && !supplied.isEmpty()) return supplied;
        }
        return scanTracks();
    }

    private List<File> scanTracks() {
        List<File> found = new ArrayList<>();
        if (!musicFolder.isDirectory()) return found;
        scanFolder(musicFolder, found);
        return found;
    }

    private void scanFolder(File folder, List<File> found) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanFolder(f, found);
            } else if (f.isFile() && isSupportedExtension(f.getName()) && !blacklist.contains(f.getName())) {
                found.add(f);
                LOGGER.info("[MusicShuffle] Found track: {}", f.getAbsolutePath());
            }
        }
    }

    private boolean isSupportedExtension(String name) {
        String lower = name.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static long countPcmFrames(File file, AudioFormat targetFormat) {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file);
             AudioInputStream pcm = AudioSystem.getAudioInputStream(targetFormat, raw)) {

            int frameSize = targetFormat.getFrameSize();
            if (frameSize <= 0) frameSize = 1;

            byte[] buf = new byte[4096 * 8];
            long frames = 0;
            int bytesRead;
            while ((bytesRead = pcm.read(buf)) != -1) {
                frames += bytesRead / frameSize;
            }
            return frames;

        } catch (Exception e) {
            return 0;
        }
    }

    private static AudioFormat toPCM(AudioFormat source) {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(),
                16,
                source.getChannels(),
                source.getChannels() * 2,
                source.getSampleRate(),
                false
        );
    }
}