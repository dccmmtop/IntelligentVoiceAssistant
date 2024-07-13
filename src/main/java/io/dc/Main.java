package io.dc;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * @author dc on 2024/7/13
 */
public class Main {
    public static void main(String[] args) {
        new CaptureThread().start();
    }
}