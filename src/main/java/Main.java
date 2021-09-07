public class Main {
	public static void main(String[] args) {
		System.out.println("Hello World");
		SerialComm.printPorts();
		SerialComm sCommObj = SerialComm.getInstance();
		// byte[] test = {0x2f, 0x5c};
		// System.out.println(SerialComm.toHexString(test));
		sCommObj.writeData(DatatypeOut.RIGHT_FRONT_MOTOR, 12984719);
		sCommObj.readDataIn();
		// System.out.println(SerialComm.toHexString(sCommObj.getData(DatatypeOut.RIGHT_FRONT_MOTOR.getValue())));
	}	
}
