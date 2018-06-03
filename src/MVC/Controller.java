package MVC;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;

import DB.*;
import Enums.*;
import Requests.*;
import Responses.*;
import ResponsesEntitys.*;

public class Controller implements Observer {
	private String url;
	private int port;
	private Model model;
	private View view;
	private HashMap<String, SocketIOClient> connections;
	private SocketHandler socketHandler;
	private Controller instance;
	private SocketIOServer serverSock;
	private PausableThreadPoolExecutor executionPool;
	

	public ResponseData execute(String data) {
		// TODO Auto-generated method stub
		RequestData reqData = socketHandler.getObjectFromString(data, RequestData.class);
		User user = model.getUser(reqData.getUserEmail());
		if(reqData.getType() != RequestType.CreateUserRequest && user == null)
			return new ErrorResponseData(ErrorType.UserIsNotExist);
		view.printToConsole(user.getEmail()+" Send "+reqData.getType());
		switch (reqData.getType()) {
		case AddFriendRequest:
			return model.AddFriend(socketHandler.getObjectFromString(data, AddFriendRequestData.class),user);// checked
		case ChangePasswordRequest:
			return model.ChangePassword(socketHandler.getObjectFromString(data, ChangePasswordRequestData.class),user);// checked
		case ConfirmEvent:
			return model.JoinEvent(socketHandler.getObjectFromString(data, ConfirmEventRequestData.class),user);
		case ContactsListRequest:
			return model.ContactList(socketHandler.getObjectFromString(data, ContactsListRequestData.class),user);// checked
		case CreateEventRequest:
			return model.CreateEvent(socketHandler.getObjectFromString(data, CreateEventRequestData.class),user);
		case CreateUserRequest:
			return model.CreateUser(socketHandler.getObjectFromString(data, CreateUserRequestData.class));
		case DeclineEvent:
			return model.DeclineEvent(socketHandler.getObjectFromString(data, DeclineEventRequestData.class),user);
		case EditContactsListRequest:
			return model.EditContactsList(socketHandler.getObjectFromString(data, EditContactsListRequestData.class),user);
		case EditUserRequest:
			return model.EditUser(socketHandler.getObjectFromString(data, EditUserRequestData.class),user);
		case EventsListRequest:
			return model.EventsList(socketHandler.getObjectFromString(data, EventsListRequestData.class),user);
		case EventProtocolRequest:
			return model.EventProtocol(socketHandler.getObjectFromString(data, EventProtocolRequestData.class),user);// TODO
		case IsUserExistRequest:
			return model.IsUserExist(socketHandler.getObjectFromString(data, IsUserExistRequestData.class),user);
		case LeaveEvent:
			return model.LeaveEvent(socketHandler.getObjectFromString(data, LeaveEventRequestData.class),user);
		case LoginRequest:
			return model.Login(socketHandler.getObjectFromString(data, LoginRequestData.class),user);
		case ProfilePictureRequest:
			return model.ProfilePicture(socketHandler.getObjectFromString(data, ProfilePictureRequestData.class),user);
		case UpdateProfilePictureRequest:
			return model.UpdateProfilePicture(socketHandler.getObjectFromString(data, UpdateProfilePictureRequestData.class),user);
		default:
			System.out.println("default");
			break;
		}
		return null;
	}

		
	private void checkIfUserHasInvites(User u) {
		ArrayList<UserEvent> unAnsweredInvites = model.getDbManager().getUserEventsWithSpecificAnswer(u.getId(), 0);
		if (unAnsweredInvites != null && unAnsweredInvites.size() != 0) {
			unAnsweredInvites.forEach(i -> {
				ArrayList<UserData> l = new ArrayList<>();
				l.add(model.getDbManager().getUserDataFromDBUserEntity(i.getUser()));
				socketHandler.sendEventInventationToUsers(model.getDbManager().getEventDataByEvent(i.getEvent(), l), l);
			});
		}
	}



	public void start() {

		startServer();
		startServerToRcieveAudio();
	}

	// C'tor
	public Controller(Model model, View view, String ip, int port) {
		super();
		this.url = ip;
		this.port = port;
		this.connections = new HashMap<>();
		this.socketHandler = new SocketHandler(connections);
		this.model = model;
		this.model.setSocketHandler(socketHandler);
		this.view = view;
		executionPool = new PausableThreadPoolExecutor(10, 20, 2, TimeUnit.MINUTES, new ArrayBlockingQueue<>(5));
		this.view.addObserver(this);
		this.model.addObserver(this);
		this.instance = this;
	}
	
	private String getClientEmailBySocket(SocketIOClient client) {
		for (String email : connections.keySet()) {
			if (connections.get(email).getSessionId().equals(client.getSessionId()))
				return email;
		}
		return null;
	}

	// Server Methods
	public void initServerFunctionality(SocketIOServer serverSock) {
		//Connect - First Connection-On Login/Register requests. Adding client's socket to connection
		serverSock.addEventListener("Connect", String.class, new DataListener<String>() {

			@Override
			public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
				// TODO Auto-generated method stub
				instance.executionPool.execute(new Runnable() {
					@Override
					public void run() {
						// TODO Auto-generated method stub
						RequestData rd = socketHandler.getObjectFromString(data, RequestData.class);
						User u = model.getDbManager().getUser(rd.getUserEmail());
						if(u != null)
						{							
							connections.put(u.getEmail(), client);
							view.printToConsole(rd.getUserEmail()+" Connected");
							ResponseData resData= instance.execute(data);
							if(resData.getType() == ResponseType.Login)//Success
								executionPool.execute(new Runnable() {
									
									@Override
									public void run() {
										// TODO Auto-generated method stub
										model.checkAndSendInvitesAfterLogin(client, model.getDbManager().getUser(rd.getUserEmail()));
									}
								});
							socketHandler.sendToClient(client, "Response",resData);//After Adding socket to connection, Handle Request
						}
							
					}
				});
			}
		});
		
		//Disconnecting - Need to delete client's socket from connection
		serverSock.addDisconnectListener(new DisconnectListener() {
			
			@Override
			public void onDisconnect(SocketIOClient client) {
				// TODO Auto-generated method stub
				String currentMail = getClientEmailBySocket(client);
				if(currentMail != null)
				{
					view.printToConsole(currentMail+ " Disconnected");
					connections.remove(currentMail);
				}
			}
		});
		
		//Reconnecting - Need to update client's socket
		serverSock.addEventListener("Reconncet", String.class, new DataListener<String>() {

			@Override
			public void onData(SocketIOClient client, String mail, AckRequest ackSender) throws Exception {
				// TODO Auto-generated method stub
				if(!client.getSessionId().equals(connections.get(mail)))
					connections.replace(mail, client);
			}
		});
		
		//Request - Check if user has connection
		serverSock.addEventListener("Request", String.class, new DataListener<String>() {

			@Override
			public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
				// TODO Auto-generated method stub
				//Check if user has connection
				instance.executionPool.execute(new Runnable() {
					
					@Override
					public void run() {
						// TODO Auto-generated method stub						
						Boolean isUserHasConnection = getClientEmailBySocket(client) != null ? true : false;
						if(isUserHasConnection)
							socketHandler.sendToClient(client, "Response", instance.execute(data));//Handle Request
					}
				});
				
			}
		});
		
		/*serverSock.addEventListener("Request", String.class, new DataListener<String>() {

			@Override
			public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
				// TODO Auto-generated method stub
				System.out.println("request" + data);
				client.sendEvent("Response", "request response");

			}
		});
		
		serverSock.addEventListener("Register", String.class, new DataListener<String>() {

			@Override
			public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
				// TODO Auto-generated method stub
				System.out.println("register" + data);
				client.sendEvent("Response", " register response");

			}
		});*/
	}

	private void startServer() {
		Configuration config = new Configuration();
		config.setHostname(url);
		config.setPort(port);
		view.printToConsole("Server Is Now Listening On " + url + ":" + port);
		serverSock = new SocketIOServer(config);
		initServerFunctionality(serverSock);
		serverSock.start();
	}

		public Model getModel() {
		return model;
	}



	@Override
	public void update(Observable o, Object arg) {
		// TODO Auto-generated method stub
		System.out.println("check1");
		if(o instanceof Model)
		{
			System.out.println("check2");
			if(arg instanceof String)
				view.printToConsole(""+arg);
			
		}
	}
	
	private void startServerToRcieveAudio() {
		{

			Thread dataSetThread = new Thread(new Runnable() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					InetAddress addr;
					try {
						addr = InetAddress.getByName(url);
						ServerSocket socket = new ServerSocket(port+1,50,addr);
						while(true)
						{
							Socket cs = socket.accept();
							handleDataSetRecord(cs);
							
						}
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
			Thread recordThread = new Thread(new Runnable() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					InetAddress addr;
					try {
						addr = InetAddress.getByName(url);
						ServerSocket socket = new ServerSocket(port+2,50,addr);
						while(true)
						{
							Socket cs = socket.accept();
							handleEventRecordRecord(cs);
						}
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
			
			dataSetThread.start();
			recordThread.start();
			

		}
	}
	
	private void handleDataSetRecord(Socket sock) 
	{
		System.out.println("DataSet");
		DataInputStream inFromClient = null;
		DataOutputStream outToClient = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			int userId= 0,length = 0,bytesSize = 0;
			byte[] arr;
			String str;
			inFromClient = new DataInputStream(sock.getInputStream());
			outToClient = new DataOutputStream(sock.getOutputStream());
			
			//user ID
			str = inFromClient.readLine();
			userId = Integer.parseInt(str);
			User u = (User) model.getDbManager().get(userId, DBEntityType.User);
			if(u != null)
				view.printToConsole(u.getEmail()+" Send DataSetRequest");
			outToClient.write(1);
			//Length in seconds
			str = inFromClient.readLine();
			length = Integer.parseInt(str);
			outToClient.write(1);
			//Byte array count
			str = inFromClient.readLine();
			bytesSize = Integer.parseInt(str);
			outToClient.write(1);
			
			arr = new byte[bytesSize];
			//inFromClient.read(arr, 0, bytesSize);
			int index = 0;
			while(index<bytesSize)
			{
				arr[index] = inFromClient.readByte();
				index++;
			}
			outToClient.write(1);
			inFromClient.close();
			outToClient.close();
			sock.close();
			model.DataSet(userId, length, arr);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			try {
				inFromClient.close();
				outToClient.close();
				sock.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

	private void handleEventRecordRecord(Socket sock) 
	{
		System.out.println("Event record");
		DataInputStream inFromClient = null;
		DataOutputStream outToClient = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			int eventId= 0,length = 0,bytesSize = 0;
			byte[] arr;
			String str;
			inFromClient = new DataInputStream(sock.getInputStream());
			outToClient = new DataOutputStream(sock.getOutputStream());
			
			//event ID
			str = inFromClient.readLine();
			eventId = Integer.parseInt(str);
			Event e = (Event) model.getDbManager().get(eventId, DBEntityType.Event);
			if(e != null)
				view.printToConsole(e.getAdmin().getEmail()+" Send CloseEventRequest");
			outToClient.write(1);
			//Byte array count
			str = inFromClient.readLine();
			bytesSize = Integer.parseInt(str);
			outToClient.write(1);
			
			arr = new byte[bytesSize];
			//inFromClient.read(arr, 0, bytesSize);
			int index = 0;
			while(index<bytesSize)
			{
				arr[index] = inFromClient.readByte();
				index++;
			}
			Path path = Paths.get("C:\\Users\\project06\\Desktop\\ProdTest.wav");
			Files.write(path, arr);	
			outToClient.write(1);
			inFromClient.close();
			outToClient.close();
			sock.close();
			model.CloseEvent(eventId, arr);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			try {
				inFromClient.close();
				outToClient.close();
				sock.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

}
