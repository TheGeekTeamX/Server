package Boot;




import MVC.*;
import Tools.BytesHandler;


public class Run {

	public static void main(String[] args) throws InterruptedException {
		
		
		/*Test t = new Test();
		t.test();*/
		
		/*BytesHandler bytesHandler = new BytesHandler();
		bytesHandler.FromImageToByteArray("./Images/1.jpg", "jpg");*/
		
		
		lunchServer(args);

		
	}
	
	public static void lunchServer(String[] args)
	{
		View view = new View();
		view.printToConsole("Server Wakes Up...Wait For Acknowledge");
		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		String rsrcPath = args[2];
		Model model = Model.getInstance(rsrcPath);		
		Controller controller = new Controller(model,view,ip,port);
		controller.start();
	}

}
