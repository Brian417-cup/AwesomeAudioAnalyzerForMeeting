package com.util;

import javax.sound.sampled.*;
import java.io.*;

public class AudioCutter {

    public static void cut(File sourceFile,
                           File targetFile,
                           double startSec,
                           double endSec) throws Exception {

        AudioInputStream originalStream =
                AudioSystem.getAudioInputStream(sourceFile);

        AudioFormat format = originalStream.getFormat();

        long bytesPerSecond =
                format.getFrameSize() * (long) format.getFrameRate();

        long startBytes = (long) (startSec * bytesPerSecond);
        long endBytes = (long) (endSec * bytesPerSecond);

        originalStream.skip(startBytes);

        long framesOfAudioToCopy =
                (endBytes - startBytes) / format.getFrameSize();

        AudioInputStream shortenedStream =
                new AudioInputStream(originalStream,
                        format,
                        framesOfAudioToCopy);

        AudioSystem.write(shortenedStream,
                AudioFileFormat.Type.WAVE,
                targetFile);
    }
}