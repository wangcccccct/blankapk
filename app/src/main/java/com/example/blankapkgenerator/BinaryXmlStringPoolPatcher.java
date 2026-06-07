package com.example.blankapkgenerator;

import java.nio.charset.StandardCharsets;

public final class BinaryXmlStringPoolPatcher {
    private static final int UTF8_FLAG = 0x00000100;

    private BinaryXmlStringPoolPatcher() {
    }

    public static byte[] replaceUtf8String(byte[] xmlBytes, String oldValue, String newValue) {
        int stringPoolOffset = 8;
        int stringCount = readIntLE(xmlBytes, stringPoolOffset + 8);
        int flags = readIntLE(xmlBytes, stringPoolOffset + 16);
        int stringsStart = readIntLE(xmlBytes, stringPoolOffset + 20);
        byte[] newUtf8 = newValue.getBytes(StandardCharsets.UTF_8);
        if ((flags & UTF8_FLAG) != 0) {
            for (int i = 0; i < stringCount; i++) {
                int stringOffset = readIntLE(xmlBytes, stringPoolOffset + 28 + (i * 4));
                int dataOffset = stringPoolOffset + stringsStart + stringOffset;
                int utf16LengthOffset = dataOffset;
                int utf16LengthSize = encodedLength8Size(xmlBytes, utf16LengthOffset);
                int utf8LengthOffset = utf16LengthOffset + utf16LengthSize;
                int utf8LengthSize = encodedLength8Size(xmlBytes, utf8LengthOffset);
                int stringDataOffset = utf8LengthOffset + utf8LengthSize;
                int byteLength = readLength8(xmlBytes, utf8LengthOffset);
                String current = new String(xmlBytes, stringDataOffset, byteLength, StandardCharsets.UTF_8);
                if (!current.equals(oldValue)) {
                    continue;
                }
                if (newUtf8.length > byteLength) {
                    throw new IllegalArgumentException("新字符串超出模板预留长度：" + oldValue);
                }
                if (newValue.length() > 0x7f || newUtf8.length > 0x7f) {
                    throw new IllegalArgumentException("当前模板只支持 127 字节以内的替换字符串");
                }
                writeOneByteLength(xmlBytes, utf16LengthOffset, newValue.length());
                writeOneByteLength(xmlBytes, utf8LengthOffset, newUtf8.length);
                System.arraycopy(newUtf8, 0, xmlBytes, stringDataOffset, newUtf8.length);
                for (int j = stringDataOffset + newUtf8.length; j < stringDataOffset + byteLength; j++) {
                    xmlBytes[j] = 0;
                }
                xmlBytes[stringDataOffset + byteLength] = 0;
                return xmlBytes;
            }
        } else {
            byte[] newUtf16 = newValue.getBytes(StandardCharsets.UTF_16LE);
            for (int i = 0; i < stringCount; i++) {
                int stringOffset = readIntLE(xmlBytes, stringPoolOffset + 28 + (i * 4));
                int dataOffset = stringPoolOffset + stringsStart + stringOffset;
                int utf16LengthOffset = dataOffset;
                int utf16LengthSize = encodedLength16Size(xmlBytes, utf16LengthOffset);
                int stringDataOffset = utf16LengthOffset + utf16LengthSize;
                int charLength = readLength16(xmlBytes, utf16LengthOffset);
                int byteLength = charLength * 2;
                String current = new String(xmlBytes, stringDataOffset, byteLength, StandardCharsets.UTF_16LE);
                if (!current.equals(oldValue)) {
                    continue;
                }
                if (newUtf16.length > byteLength) {
                    throw new IllegalArgumentException("新字符串超出模板预留长度：" + oldValue);
                }
                if (newValue.length() > 0x7fff) {
                    throw new IllegalArgumentException("当前模板不支持过长 UTF-16 字符串");
                }
                writeOneWordLength(xmlBytes, utf16LengthOffset, newValue.length());
                System.arraycopy(newUtf16, 0, xmlBytes, stringDataOffset, newUtf16.length);
                for (int j = stringDataOffset + newUtf16.length; j < stringDataOffset + byteLength + 2; j++) {
                    xmlBytes[j] = 0;
                }
                return xmlBytes;
            }
        }
        throw new IllegalStateException("模板里没找到占位字符串：" + oldValue);
    }

    private static int encodedLength8Size(byte[] data, int offset) {
        return (data[offset] & 0x80) == 0 ? 1 : 2;
    }

    private static int readLength8(byte[] data, int offset) {
        int first = data[offset] & 0xff;
        if ((first & 0x80) == 0) {
            return first;
        }
        int second = data[offset + 1] & 0xff;
        return ((first & 0x7f) << 8) | second;
    }

    private static int encodedLength16Size(byte[] data, int offset) {
        int first = readShortLE(data, offset);
        return (first & 0x8000) == 0 ? 2 : 4;
    }

    private static int readLength16(byte[] data, int offset) {
        int first = readShortLE(data, offset);
        if ((first & 0x8000) == 0) {
            return first;
        }
        int second = readShortLE(data, offset + 2);
        return ((first & 0x7fff) << 16) | second;
    }

    private static void writeOneByteLength(byte[] data, int offset, int value) {
        if (value < 0 || value > 0x7f) {
            throw new IllegalArgumentException("长度超出单字节范围");
        }
        data[offset] = (byte) value;
    }

    private static void writeOneWordLength(byte[] data, int offset, int value) {
        if (value < 0 || value > 0x7fff) {
            throw new IllegalArgumentException("长度超出单字范围");
        }
        writeShortLE(data, offset, value);
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private static int readShortLE(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static void writeShortLE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
    }
}
