package com.ailove.unit;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.ailove.tts.AiAudioLabel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 音频隐式标识：ID3v2.4 标签结构可被标准方式解析，原始音频字节完整保留。
 */
class AiAudioLabelTest {

    @Test
    void 标签头与帧结构符合ID3v2_4规范_且保留原始音频() {
        byte[] mp3 = {(byte) 0xFF, (byte) 0xFB, 0x10, 0x11, 0x12};
        byte[] tagged = AiAudioLabel.tagMp3(mp3);

        assertEquals('I', tagged[0]);
        assertEquals('D', tagged[1]);
        assertEquals('3', tagged[2]);
        assertEquals(4, tagged[3]); // 主版本 v2.4
        assertEquals(0, tagged[5]); // 无标志位

        int tagSize = AiAudioLabel.readSynchsafe(tagged, 6);
        assertEquals(tagged.length - 10 - mp3.length, tagSize);

        String framesText = new String(Arrays.copyOfRange(tagged, 10, 10 + tagSize),
                StandardCharsets.UTF_8);
        assertTrue(framesText.startsWith("TXXX"), "第一个帧应为 TXXX");
        assertTrue(framesText.contains("AiGeneratedContent"));
        assertTrue(framesText.contains("true"));
        assertTrue(framesText.contains("COMM"));
        assertTrue(framesText.contains("内容由人工智能生成"));

        assertArrayEquals(mp3, Arrays.copyOfRange(tagged, 10 + tagSize, tagged.length));
    }

    @Test
    void 空输入原样返回_大标签尺寸仍为合法synchsafe编码() {
        assertArrayEquals(new byte[0], AiAudioLabel.tagMp3(new byte[0]));

        byte[] big = new byte[2_000_000];
        byte[] tagged = AiAudioLabel.tagMp3(big);
        for (int i = 6; i < 10; i++) {
            assertTrue((tagged[i] & 0x80) == 0, "synchsafe 字节最高位必须为 0");
        }
        int tagSize = AiAudioLabel.readSynchsafe(tagged, 6);
        assertEquals(big.length, tagged.length - 10 - tagSize);
    }
}
