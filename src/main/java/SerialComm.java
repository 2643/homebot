import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.bind.DatatypeConverter;

import com.fazecast.jSerialComm.*;

public class SerialComm {
    private static SerialComm single_instance = null;
    private static SerialPort SerialIOPort = SerialPort.getCommPort("ttyACM0");
    private static int numDatatypes = 0x25+2;
    private static byte PKT_LEN = 0x11;

    // init 2d array for each item + 2 status datatypes
    private static byte[][] dataBuffer = new byte[numDatatypes][8];
    private static ReentrantLock[] mutexes = new ReentrantLock[numDatatypes];
    private static byte packetNumOut = 0x00; // allow to overflow.
    // we'll see if I need writeLock, much more of a pain to implement. - Probably an Asian
    // private static ReentrantLock writeLock = new ReentrantLock();
    private static Semaphore writeLock = new Semaphore(1);

    private static byte packetNumIn = 0x00; //allow to overflow

    public static byte[] HEADER = {0x2f, 0x5c};
    public static byte[] VERSION = {0x00};
    public static byte[] TRAILER = {(byte)0xad};

    // ... |2HDR|1VER|1PKT#|1PKT_LEN(FUTURE_USE)|1DT|8DATA|2CHKSUM|1TRL| ...
    // ... |0000|2222|33333|44444444444444444444|555|66666|ddddddd|ffff|
    public static void main(String[] args) {
    }

    public static SerialComm getInstance()
    {
        if (single_instance == null) {
            // init mutexes to prevent corrupted data read
            for(int i = 0; i < (numDatatypes); i++) {
                mutexes[i] = new ReentrantLock();
            }
            single_instance = new SerialComm();
            SerialIOPort.setBaudRate(115200);
        }
        return single_instance;
    }

    public void writeData(DatatypeOut DTOut, long data) {
        byte[] dataArray = longToByteArray(data);
        writeData(DTOut, dataArray);
    }

    public void writeData(DatatypeOut DTOut, byte[] dataArray) {
        byte[] writeBuffer = new byte[17];
        System.arraycopy(HEADER, 0, writeBuffer, 0, HEADER.length);
        System.arraycopy(VERSION, 0, writeBuffer, HEADER.length, VERSION.length);
        writeBuffer[3] = packetNumOut++; // ret then increment
        writeBuffer[4] = PKT_LEN;
        writeBuffer[5] = (byte)DTOut.getValue();
        System.arraycopy(dataArray, 0, writeBuffer, 6, dataArray.length);

        long sum = 0;
        for (int i = 0; i < 14; i++) {
            sum += Byte.toUnsignedInt(writeBuffer[i]);
        }
        System.arraycopy(longToByteArray(sum), 6, writeBuffer, 14, 2);

        System.arraycopy(TRAILER, 0, writeBuffer, 16, TRAILER.length);

        System.out.println(toHexString(writeBuffer));

        try {
            writeLock.acquire();
            SerialIOPort.writeBytes(writeBuffer, 17);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void readDataIn() {
        if (SerialIOPort.bytesAvailable() < PKT_LEN) {
            return;
        }

        byte[] readBuffer = new byte[PKT_LEN];

        SerialIOPort.readBytes(readBuffer, PKT_LEN);
        // readBuffer = toByteArray("2F5C000011010000000000C6218F0213AD");
        System.out.println(toHexString(readBuffer));

        boolean valid = true;
        
        if (readBuffer[0] != HEADER[0]) {
            valid = false;
        }
        if (readBuffer[1] != HEADER[1]) {
            valid = false;
        }
        if (readBuffer[2] != VERSION[0]) {
            valid = false;
        }
        if (readBuffer[3] != packetNumIn) {
            valid = false;
        }
        if (readBuffer[PKT_LEN-1] != TRAILER[0]) {
            valid = false;
        }

        if (!valid) {
            writeData(DatatypeOut.ERROR, 0);
            System.exit(1);
        }

        long sum = 0;
        for (int i = 0; i < 14; i++) {
            sum += Byte.toUnsignedInt(readBuffer[i]);
        }

        byte[] checksum = longToByteArray(sum);
        if (readBuffer[0x0e] != checksum[6]) {
            valid = false;
        }
        if (readBuffer[0x0f] != checksum[7]) {
            valid = false;
        }

        if (!valid) {
            writeData(DatatypeOut.ERROR, 0);
            System.exit(1);
        }

        int inputDatatype = Byte.toUnsignedInt(readBuffer[0x05]);
        if (inputDatatype == DatatypeIn.ERROR.getValue()) {
            System.err.println("ERROR");
            System.exit(1);
        }
        else if (inputDatatype == DatatypeIn.READY.getValue()) {
            writeLock.release();
        }
        else {
            mutexes[inputDatatype].lock();
            try {
                System.arraycopy(readBuffer, 6, dataBuffer[inputDatatype], 0, 8);
                System.out.println(toHexString(dataBuffer[inputDatatype]));
            }
            finally {
                mutexes[inputDatatype].unlock();
            }
        }
    }
    
    public byte[] getData(DatatypeIn ID) {
       return getData(ID.getValue());
    }

    public byte[] getData(int ID) {
        byte[] dataOut = new byte[8];

        mutexes[ID].lock();
        try {
            dataOut = dataBuffer[ID];
        } finally {
            mutexes[ID].unlock();
        }

        return dataOut;
    }

    public static void printPorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port: ports) {
            System.out.println(port);
        }
    }

    public static String toHexString(byte[] array) {
        return DatatypeConverter.printHexBinary(array);
    }
    
    public static byte[] toByteArray(String s) {
        return DatatypeConverter.parseHexBinary(s);
    }

    public static byte[] longToByteArray(long l) {
        ByteBuffer b = ByteBuffer.allocate(8);
        b.order(ByteOrder.BIG_ENDIAN);
        b.putLong(l);
        return b.array();
    }
}
