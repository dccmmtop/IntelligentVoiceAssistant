package io.dc;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class CaptureThread extends Thread {
    AudioFormat audioFormat;
    TargetDataLine targetDataLine;
    boolean flag = true;

    private String audioToText(AudioFile audioFile) {

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("file", FileUtil.file(audioFile.getAbFileName()));
        paramMap.put("language", "zh");
        paramMap.put("model", "medium");
        paramMap.put("response_format", "text");

        String result = HttpUtil.post("http://127.0.0.1:9977/api", paramMap);
        JSONObject json = JSONUtil.parseObj(result);
        System.out.println("语音转文字结束!!!");
        return (String) json.get("data");
    }

    private AudioFile saveAudioFile(ByteArrayOutputStream baos, AudioInputStream ais) throws IOException {
        System.out.println("停止录入");
        audioFormat = getAudioFormat();
        byte audioData[] = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        ais = new AudioInputStream(bais, audioFormat, audioData.length / audioFormat.getFrameSize());
        //定义最终保存的文件名
        System.out.println("开始生成语音文件");
        String fileName = System.currentTimeMillis() + ".wav";
        String abFileName = new File("").getCanonicalPath() + File.separator + fileName;
        File audioFile = new File(abFileName);
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audioFile);
        AudioFile file = new AudioFile();
        file.setAbFileName(abFileName);
        file.setFileName(fileName);
        return file;
    }

    @Override
    public void run() {
        AudioFileFormat.Type fileType = AudioFileFormat.Type.WAVE;
        //声音录入的权值
        int weight = 3;
        //静音的次数
        int slient = 0;

        ByteArrayInputStream bais = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AudioInputStream ais = null;
        audioFormat = getAudioFormat();
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
        try {
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        try {
            targetDataLine.open(audioFormat);
            targetDataLine.start();
            byte[] fragment = new byte[1024];

            ais = new AudioInputStream(targetDataLine);
            boolean start = false;
            int current = 0;
            while (flag) {
                //拾取声音
                targetDataLine.read(fragment, 0, fragment.length);
                // 本次拾取声音的最后一位信息
                current = Math.abs(fragment[fragment.length - 1]);

                // 如果大于阈值，认为在讲话
                if (current > weight) {
                    start = true;
                }
                // 如果正在讲话，就写入缓存
                if (start) {
                    baos.write(fragment);
                }
                //判断语音是否停止
                if (current <= weight) {
                    // System.out.println("无声音");
                    slient++;
                } else {
                    System.out.println("有声音");
                    slient = 0;
                }
                //计数超过20说明此段时间没有声音传入(值也可更改)
                if (start && slient > 20) {
                    start = false;
                    AudioFile audioFile = saveAudioFile(baos, ais);
                    String text = audioToText(audioFile);
                    System.out.println(text);
                    // 删除生成的临时文件
                    delTmpAudioFile(audioFile);
                    // 重置
                    slient = 0;
                    baos.reset();
                }
            }

            //取得录音输入流
            // stopRecognize();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //关闭流
            try {
                ais.close();
                bais.close();
                baos.reset();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private AudioFormat getAudioFormat() {
        float sampleRate = 8000;
        // 8000,11025,16000,22050,44100
        int sampleSizeInBits = 16;
        // 8,16
        int channels = 1;
        // 1,2
        boolean signed = true;
        // true,false
        boolean bigEndian = false;
        // true,false
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    private void delTmpAudioFile(AudioFile audioFile) {
        FileUtil.del(audioFile.getAbFileName());
    }
}