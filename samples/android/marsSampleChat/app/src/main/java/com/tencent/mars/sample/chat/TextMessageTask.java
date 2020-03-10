/*
 * Tencent is pleased to support the open source community by making Mars available.
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.mars.sample.chat;

import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.widget.Toast;

import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.LogUtils;
import com.tencent.mars.sample.SampleApplicaton;
import com.tencent.mars.sample.chat.proto.Chat;
import com.tencent.mars.sample.proto.Main;
import com.tencent.mars.sample.utils.print.MemoryDump;
import com.tencent.mars.sample.wrapper.TaskProperty;
import com.tencent.mars.sample.wrapper.remote.NanoMarsTaskWrapper;
import com.tencent.mars.stn.StnLogic;
import com.tencent.mars.xlog.Log;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Text messaging task
 * <p/>
 * Created by zhaoyuan on 16/2/29.
 */
@TaskProperty(
        host = "120.25.238.4",
        path = "/mars/sendmessage",
        cmdID = Main.CmdID.CMD_ID_SEND_MESSAGE_VALUE,
        longChannelSupport = true,
        shortChannelSupport = false
)
public class TextMessageTask extends NanoMarsTaskWrapper<Chat.SendMessageRequest.Builder, Chat.SendMessageResponse.Builder> {

    /**
     * 用来获取taskId也就是seq
     */
    private static AtomicInteger ai = new AtomicInteger(0);
    private final int taskID;
    //手机号
    private String phone;
    private byte[] body;


    private Runnable callback = null;

    private Runnable onOK = null;
    private Runnable onError = null;

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public TextMessageTask(String phone, byte[] body) {
        super(Chat.SendMessageRequest.newBuilder(), Chat.SendMessageResponse.newBuilder());
        this.taskID = ai.incrementAndGet();
        this.phone = phone;
        this.body = body;
        request.setAccessToken("test_token");
        request.setFrom(SampleApplicaton.accountInfo.userName);
        request.setTo("all");
        request.setText("text");
        request.setTopic("topicName");

    }

    @Override
    public void onPreEncode(Chat.SendMessageRequest.Builder request) {
        // TODO: Not thread-safe here
    }

    @Override
    public void onPostDecode(Chat.SendMessageResponse.Builder response) {
//        if (response.getErrCode() == Chat.SendMessageResponse.Error.ERR_OK_VALUE) {
        callback = onOK;
//
//        } else {
//            callback = onError;
//        }
    }

    @Override
    public byte[] req2buf() {
        byte[] byPhone = null;
        if (!TextUtils.isEmpty(phone) && phone.length() == 11) {
            //构造手机BCD码
            byPhone = str2Bcd(phone);
        }
        //body最小长度=校验码+包尾
        int byteSize = 2;
        if (body != null && body.length > 0) {
            byteSize += body.length;
        }
        if (byPhone != null && byPhone.length > 0) {
            byteSize += byPhone.length;
        }
        byte[] result = new byte[byteSize];
        //手机号码，6个字节
        if (byPhone != null) {
            System.arraycopy(byPhone, 0, result, 3, byPhone.length);
        }
        //body，任意字节
        if (body != null) {
            System.arraycopy(body, 0, result, byteSize - 2 - body.length, body.length);
        }
        byte[] marsHeader = getMockMarsHeader(this.taskID, byteSize);
        //根据allByte生成校验码
        byte[] allByte = new byte[marsHeader.length + result.length - 2];
        System.arraycopy(marsHeader, 0, allByte, 0, marsHeader.length);
        System.arraycopy(result, 0, allByte, marsHeader.length, result.length - 2);
        byte code = 0;
        code = getCheckCode(allByte);
        //校验码，1个字节
        result[byteSize - 2] = code;
        //包尾，1个字节
        result[byteSize - 1] = 0x0d;
        return result;
    }

    /**
     * @param n
     * @Title: intTohex
     * @Description: int型转换成16进制
     * @return: String
     */
    public static String intTohex(int n) {
        int num = 0xff & n;
        StringBuffer s = new StringBuffer();
        String a;
        char[] b = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        while (num != 0) {
            s = s.append(b[num % 16]);
            num = num / 16;
        }
        a = s.reverse().toString();
        if ("".equals(a)) {
            a = "00";
        }
        if (a.length() == 1) {
            a = "0" + a;
        }
        return a;
    }

    /**
     * 生成校验码
     *
     * @param bytes
     * @return
     */
    private byte getCheckCode(byte[] bytes) {
        byte code = 0;
        for (byte b : bytes) {
            code ^= b;
        }
        return code;
    }

    /**
     * 模拟请求头
     *
     * @param seq
     * @param bodySize
     * @return
     */
    private byte[] getMockMarsHeader(int seq, int bodySize) {
        byte[] marsHeader = new byte[22];
        marsHeader[0] = 0x29;
        marsHeader[1] = 0x29;

        //header_length
        marsHeader[2] = 0x00;
        marsHeader[3] = 0x00;
        marsHeader[4] = 0x00;
        marsHeader[5] = 0x16;

        //client_version
        marsHeader[6] = 0x00;
        marsHeader[7] = 0x00;
        marsHeader[8] = 0x00;
        marsHeader[9] = (byte) 0xc8;

        //cmdid
        marsHeader[10] = 0x00;
        marsHeader[11] = 0x00;
        marsHeader[12] = 0x00;
        marsHeader[13] = 0x03;

        byte[] seqArray = getLength(seq);
        //seq
//        marsHeader[14] = 0x00;
//        marsHeader[15] = 0x00;
//        marsHeader[16] = 0x00;
//        marsHeader[17] = 0x02;
        System.arraycopy(seqArray, 0, marsHeader, 14, seqArray.length);

        //body_length
        byte[] bodyLength = getLength(bodySize);
        System.arraycopy(bodyLength, 0, marsHeader, marsHeader.length - bodyLength.length, bodyLength.length);
        return marsHeader;
    }

    private byte[] getLength(int num) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (num >> 24);
        bytes[1] = (byte) (num >> 16);
        bytes[2] = (byte) (num >> 8);
        bytes[3] = (byte) num;
        return bytes;
    }

    /**
     * 获取包长
     *
     * @param bodySize
     * @return
     */
    private byte[] getBodyLength(int bodySize) {
        byte[] size = new byte[]{0x00, 0x00};
        try {
            size[0] = (byte) (bodySize >> 8);
            size[1] = (byte) bodySize;
        } catch (Exception e) {
            e.printStackTrace();
            size = new byte[]{0x00, 0x00};
        }
        return size;
    }

    @Override
    public int buf2resp(byte[] bytesOrigin) {
        try {
            int[] bytes = new int[bytesOrigin.length];
            for (int i = 0; i < bytesOrigin.length; i++) {
                bytes[i] = 0xff & bytesOrigin[i];
            }
            onPostDecode(null);
            return StnLogic.RESP_FAIL_HANDLE_NORMAL;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return StnLogic.RESP_FAIL_HANDLE_TASK_END;
    }

    @Override
    public void onTaskEnd(int errType, int errCode) {
        if (callback == null) {
            callback = onError;
        }

        uiHandler.post(callback);
    }

    @Override
    public int getTaskId() throws RemoteException {
        return this.taskID;
    }

    public TextMessageTask onOK(Runnable onOK) {
        this.onOK = onOK;
        return this;
    }

    public TextMessageTask onError(Runnable onError) {
        this.onError = onError;
        return this;
    }

    /**
     * @功能: 10进制串转为BCD码
     * @参数: 10进制串
     * @结果: BCD码
     */
    public static byte[] str2Bcd(String asc) {
        int len = asc.length();
        int mod = len % 2;
        if (mod != 0) {
            asc = "0" + asc;
            len = asc.length();
        }
        byte abt[] = new byte[len];
        if (len >= 2) {
            len = len / 2;
        }
        byte bbt[] = new byte[len];
        abt = asc.getBytes();
        int j, k;
        for (int p = 0; p < asc.length() / 2; p++) {
            if ((abt[2 * p] >= '0') && (abt[2 * p] <= '9')) {
                j = abt[2 * p] - '0';
            } else if ((abt[2 * p] >= 'a') && (abt[2 * p] <= 'z')) {
                j = abt[2 * p] - 'a' + 0x0a;
            } else {
                j = abt[2 * p] - 'A' + 0x0a;
            }
            if ((abt[2 * p + 1] >= '0') && (abt[2 * p + 1] <= '9')) {
                k = abt[2 * p + 1] - '0';
            } else if ((abt[2 * p + 1] >= 'a') && (abt[2 * p + 1] <= 'z')) {
                k = abt[2 * p + 1] - 'a' + 0x0a;
            } else {
                k = abt[2 * p + 1] - 'A' + 0x0a;
            }
            int a = (j << 4) + k;
            byte b = (byte) a;
            bbt[p] = b;
        }
        return bbt;
    }
}
