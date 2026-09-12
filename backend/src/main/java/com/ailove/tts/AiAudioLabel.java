package com.ailove.tts;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * AI 生成音频的隐式标识（《人工智能生成合成内容标识办法》要求文件元数据级标识）。
 * 在 MP3 字节流前写入 ID3v2.4 标签：TXXX(AiGeneratedContent=true) + COMM 中文说明，
 * 标识随文件复制/转发持续存在，标准解析器（浏览器、播放器、ffprobe）均可读取。
 * v2.4 的帧长/标签长均为 synchsafe 编码，且正文原生支持 UTF-8。
 */
public final class AiAudioLabel {

    private static final byte ENCODING_UTF8 = 0x03;

    private AiAudioLabel() {
    }

    /** 返回带 AI 隐式标识的 MP3 字节；空输入原样返回。若上游已自带 ID3 标签，本标签依然前置（解析器读首个标签）。 */
    public static byte[] tagMp3(byte[] mp3) {
        if (mp3 == null || mp3.length == 0) {
            return mp3;
        }
        byte[] txxx = frame("TXXX", utf8("AiGeneratedContent"), new byte[] {0}, utf8("true"));
        byte[] comm = frame("COMM", new byte[] {'z', 'h', 'o'}, new byte[] {0},
                utf8("内容由人工智能生成"));
        byte[] frames = concat(txxx, comm);

        byte[] out = new byte[10 + frames.length + mp3.length];
        out[0] = 'I';
        out[1] = 'D';
        out[2] = '3';
        out[3] = 4;
        out[4] = 0;
        out[5] = 0; // 标志位
        System.arraycopy(synchsafe(frames.length), 0, out, 6, 4);
        System.arraycopy(frames, 0, out, 10, frames.length);
        System.arraycopy(mp3, 0, out, 10 + frames.length, mp3.length);
        return out;
    }

    /** ID3v2.4 帧：4 字节 ID + 4 字节 synchsafe 长度 + 2 字节标志 + 正文（编码字节在前，字段间以 0 分隔）。 */
    private static byte[] frame(String id, byte[]... fields) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(ENCODING_UTF8);
        for (byte[] field : fields) {
            body.writeBytes(field);
        }
        byte[] b = body.toByteArray();
        byte[] frame = new byte[10 + b.length];
        System.arraycopy(utf8(id), 0, frame, 0, 4);
        System.arraycopy(synchsafe(b.length), 0, frame, 4, 4);
        System.arraycopy(b, 0, frame, 10, b.length);
        return frame;
    }

    /** synchsafe 整数：每字节仅用低 7 位（规范要求，避免出现 0xFF 同步字冲突）。 */
    static byte[] synchsafe(int value) {
        return new byte[] {
                (byte) ((value >> 21) & 0x7F),
                (byte) ((value >> 14) & 0x7F),
                (byte) ((value >> 7) & 0x7F),
                (byte) (value & 0x7F),
        };
    }

    /** 读取 synchsafe 整数(供标签结构测试解析用)。 */
    public static int readSynchsafe(byte[] b, int off) {
        return ((b[off] & 0x7F) << 21) | ((b[off + 1] & 0x7F) << 14)
                | ((b[off + 2] & 0x7F) << 7) | (b[off + 3] & 0x7F);
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
