package MVC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.corundumstudio.socketio.*;
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
	
	private SocketIOServer serverSock;
	private PausableThreadPoolExecutor executionPool;
	

	public ResponseData execute(String data) {
		// TODO Auto-generated method stub
		RequestData reqData = socketHandler.getObjectFromString(data, RequestData.class);
		User user = model.getUser(reqData.getUserEmail());
		if(reqData.getType() != RequestType.CreateUserRequest && user == null)
			return new ErrorResponseData(ErrorType.UserIsNotExist);
		switch (reqData.getType()) {
		case AddFriendRequest:
			return model.AddFriend(socketHandler.getObjectFromString(data, AddFriendRequestData.class),user);// checked
		case ChangePasswordRequest:
			return model.ChangePassword(socketHandler.getObjectFromString(data, ChangePasswordRequestData.class),user);// checked
		case CloseEventRequest:
			return model.CloseEvent(socketHandler.getObjectFromString(data, CloseEventRequestData.class),user);
		case ConfirmEvent:
			return model.JoinEvent(socketHandler.getObjectFromString(data, ConfirmEventRequestData.class),user);
		case ContactsListRequest:
			return model.ContactList(socketHandler.getObjectFromString(data, ContactsListRequestData.class),user);// checked
		case CreateEventRequest:
			return model.CreateEvent(socketHandler.getObjectFromString(data, CreateEventRequestData.class),user);
		case CreateUserRequest:
			return model.CreateUser(socketHandler.getObjectFromString(data, CreateUserRequestData.class));
		case DataSetRequest:
			return model.DataSet(socketHandler.getObjectFromString(data, DataSetRequestData.class), user);
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
		ArrayList<UserEvent> unAnsweredInvites = model.getDbManager().getUnAnsweredInvites(u.getId());
		if (unAnsweredInvites != null && unAnsweredInvites.size() != 0) {
			unAnsweredInvites.forEach(i -> {
				ArrayList<UserData> l = new ArrayList<>();
				l.add(model.getDbManager().getUserDataFromDBUserEntity(i.getUser()));
				socketHandler.sendEventInventationToUsers(model.getEventData(i.getEvent(), l), l);
			});
		}
	}



	public void start() {

		startServer();
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
		this.model.addObserver(this);;
		this.view = view;
		executionPool = new PausableThreadPoolExecutor(10, 20, 2, TimeUnit.MINUTES, new ArrayBlockingQueue<>(5));
	}
	
	private String getClientEmailBySocket(SocketIOClient client) {
		for (String email : connections.keySet()) {
			if (connections.get(email).equals(client))
				return email;
		}
		return null;
	}

	// Server Methods
	public void initServerFunctionality(SocketIOServer serverSock) {
		serverSock.addDisconnectListener(new DisconnectListener() {

			@Override
			public void onDisconnect(SocketIOClient client) {
				// TODO Auto-generated method stub
				String email = getClientEmailBySocket(client);
				if (email != null) {
					view.printToConsole(email + " Is Disconnected");
					connections.remove(email);
				}
			}
		});
		// Login-First Connection
		serverSock.addEventListener("Login", String.class, new DataListener<String>() {
			@Override
			public void onData(SocketIOClient client, String data, AckRequest ackRequest) {
				// TODO Auto-generated method stub
				executionPool.execute(new Runnable() {
					@Override
					public void run() {
						// TODO Auto-generated method stub
						LoginRequestData lrd = socketHandler.getObjectFromString(data, LoginRequestData.class);
						Boolean res = Model.isEmailsEquals(getClientEmailBySocket(client), lrd.getUserEmail());
						if (getClientEmailBySocket(client) != null && res)
						{
							socketHandler.sendToClient(client, "Response",
									new ErrorResponseData(ErrorType.ConnectionIsAlreadyEstablished));
							return;
						} else {
							if (getClientEmailBySocket(client) != null
									&& !res)
								connections.remove(getClientEmailBySocket(client));
							User user = model.getDbManager().getUser(lrd.getUserEmail());
							if (user == null) {
								socketHandler.sendToClient(client, "Response",
										new ErrorResponseData(ErrorType.UserIsNotExist));
								return;
							}
							if (connections.get(user.getEmail()) == null) {
								Credential credential = model.getDbManager().getCredential(user.getId());
								if (credential == null) {
									socketHandler.sendToClient(client, "Response",
											new ErrorResponseData(ErrorType.TechnicalError));
									return;
								}
								if (Model.isEmailsEquals(lrd.getUserEmail(), credential.getUser().getEmail())
										&& lrd.getPassword().equals(credential.getCredntial())) {
									socketHandler.sendToClient(client, "Response",
											new LoginResponseData(user.getId(), user.getFirstName(), user.getLastName(),
													user.getPhoneNumber(),
													model.getDbManager().getProfilePictureUrlByUserId(user.getId())));
									view.printToConsole(credential.getUser().getEmail() + " Is Connected");
									connections.put(user.getEmail(), client);
									checkIfUserHasInvites(user);
								} else
									socketHandler.sendToClient(client, "Response",new ErrorResponseData(ErrorType.IncorrectCredentials));
								return;
							}
						}
					}
				});

			}
		});
		serverSock.addEventListener("Register", String.class, new DataListener<String>() {

			@Override
			public void onData(SocketIOClient client, String data, AckRequest ackRequest){
				// TODO Auto-generated method stub
				CreateUserRequestData reqData = socketHandler.getObjectFromString(data, CreateUserRequestData.class);
				String email = reqData.getUserEmail();
				executionPool.execute(new Runnable() {
					
					@Override
					public void run() {
						// TODO Auto-generated method stub
						ResponseData rd = execute(data);
						if(rd.getType() == ResponseType.Boolean)
							connections.put(email, client);
						socketHandler.sendToClient(client, "Response", rd);
					}
				});
			}
		});
		serverSock.addEventListener("Request", String.class, new DataListener<String>() {
			@Override
			public void onData(SocketIOClient client, String data, AckRequest ackRequest) {
				// TODO Auto-generated method stub
				RequestData rd = socketHandler.getObjectFromString(data, RequestData.class);
				if (Model.isEmailsEquals(getClientEmailBySocket(client), rd.getUserEmail())) {
					executionPool.execute(new Runnable() {

						@Override
						public void run() {
							// TODO Auto-generated method stub
							socketHandler.sendToClient(client, "Response", execute(data));
						}
					});
				} else
					socketHandler.sendToClient(client, "Response", new ErrorResponseData(ErrorType.UserMustToLogin));

			}
		});
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


}
