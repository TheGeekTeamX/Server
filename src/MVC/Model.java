package MVC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;

import com.corundumstudio.socketio.SocketIOClient;
import com.sun.xml.bind.v2.runtime.unmarshaller.XsiNilLoader.Array;

import DB.*;
import Enums.DBEntityType;
import Enums.ErrorType;
import Notifications.EventInvitationNotificationData;
import Recognize.RecognizeManager;
import Requests.*;
import Responses.*;
import ResponsesEntitys.EventData;
import ResponsesEntitys.ProtocolLine;
import ResponsesEntitys.UserData;

public class Model extends Observable {
	private static Model instance;
	private DBManager dbManager;
	private SocketHandler socketHandler;
	private RecognizeManager recognizeManager;
	private FilesHandler filesHandler;
	private String path ;
	
	
	
	public SocketHandler getSocketHandler() {
		return socketHandler;
	}

	public void setSocketHandler(SocketHandler socketHandler) {
		this.socketHandler = socketHandler;
	}

	
	public User getUser(String email)
	{
		return dbManager.getUser(email);
	}
	

	public static Model getInstance(String path)
	{
		if(instance == null)
		{
			instance = new Model(DBManager.getInstance(),path);
			instance.init();
		}
		return instance;
	}
	private void init()
	{
		dbManager = DBManager.getInstance();
	}

	public DBManager getDbManager() {
		return dbManager;
	}
	public Model(DBManager dbm,String path) {
		super();
		this.dbManager = dbm;
		recognizeManager = new RecognizeManager(path);
		filesHandler = new FilesHandler(path);
		this.path = path;
	}

	public ResponseData Login(LoginRequestData lrd , User u)
	{
		Credential credential = dbManager.getCredential(u.getId());
		if(credential != null)
		{
			if(credential.getCredntial().compareTo(lrd.getPassword())==0)
				return new LoginResponseData(dbManager.getUserDataFromDBUserEntity(u));
			else
				return new ErrorResponseData(ErrorType.IncorrectCredentials);
		}
		return new ErrorResponseData(ErrorType.TechnicalError);
	}
	
	public ResponseData EventProtocol(EventProtocolRequestData reqData,User user) {
		ArrayList<ProtocolLine> protocol = filesHandler.fromTextFileToProtocol(reqData.getEventID()+".txt");
		return protocol != null ? new EventProtocolResponseData(reqData.getEventID(), protocol)
				: new ErrorResponseData(ErrorType.TechnicalError);
	}

	public ResponseData CreateEvent(CreateEventRequestData reqData,User user) {
		
		ArrayList<String> participantsEmail = (ArrayList<String>) reqData.getUsersEmails();
		//Add Event to DB
		Event event = new Event(user, reqData.getTitle(), new Date(Calendar.getInstance().getTime().getTime()).toString(), 0, 0, reqData.getDescription());
		int eventId = dbManager.addToDataBase(event);
		if(eventId < 0)
			return new ErrorResponseData(ErrorType.TechnicalError);
		//Add UserEvents
		ArrayList<UserData> usersDataList = new ArrayList<>();
		participantsEmail.forEach(p-> {
			User u = dbManager.getUser(p);
			if(u != null)
			{
				usersDataList.add(dbManager.getUserDataFromDBUserEntity(u));
				int id = dbManager.addToDataBase(new UserEvent(u,event,0));
			}
		});
		dbManager.addToDataBase(new UserEvent(user,event,1));
		ArrayList<UserData> initialList = new ArrayList<>();
		initialList.add(dbManager.getUserDataFromDBUserEntity(user));
		//Send Invites
		EventData ed = dbManager.getEventDataByEvent(event, initialList);
		socketHandler.sendEventInventationToUsers(ed, usersDataList);
		return new CreateEventResponseData(eventId);
	}
	
	public ResponseData IsUserExist(IsUserExistRequestData reqData,User user) {
		User otherUser = dbManager.getUser(reqData.getEmail());
		if (otherUser == null)
			return new ErrorResponseData(ErrorType.FriendIsNotExist);
		return new IsUserExistResponseData(dbManager.getUserDataFromDBUserEntity(otherUser));
	}
	
	public ResponseData ProfilePicture(ProfilePictureRequestData reqData,User user) {
		byte[] arr = filesHandler.FromImageToByteArray(user.getId()+".jpg","jpg");
		if(arr != null && arr.length > 0)
			return new ProfilePictureResponseData(arr);
		return new ErrorResponseData(ErrorType.TechnicalError);
	}
	
	public ResponseData CreateUser(CreateUserRequestData reqData) {
		User user = dbManager.getUser(reqData.getUserEmail());
		if(user == null)
		{			
			User u = new User(reqData.getUserEmail(), reqData.getFirstName(), reqData.getLastName(),reqData.getPhoneNumber(), reqData.getCountry());
			u.setId(dbManager.addToDataBase(u));
			if (u.getId() < 0)
				return new ErrorResponseData(ErrorType.TechnicalError);
			byte[] bytes = reqData.getImageBytes();
			if(bytes != null)
				filesHandler.SaveByteArrayInDestinationAsImage(bytes, "jpg", "/Images/"+u.getId()+".jpg");
			if(dbManager.addToDataBase(new Credential(u, reqData.getCredential())) < 0)
				return new ErrorResponseData(ErrorType.TechnicalError);
			return new BooleanResponseData(true);
		}
		else
			return new ErrorResponseData(ErrorType.EmailAlreadyRegistered);
	}

	public ResponseData ContactList(ContactsListRequestData reqData,User user) {
		ArrayList<Contact> contactsList = dbManager.getContactsList(user.getId());
		LinkedList<UserData> list = new LinkedList<>();
		contactsList.forEach(c -> {
			User u = (User) dbManager.get(c.getFriend().getId(), DBEntityType.User);
			if (u != null) {
				list.add(dbManager.getUserDataFromDBUserEntity(u));
			}

		});
		return new ContactsListResponseData(list);
	}

	public ResponseData EditUser(EditUserRequestData reqData,User user) {
		user.setCountry(reqData.getCountry());
		user.setPhoneNumber(reqData.getPhoneNumber());
		user.setFirstName(reqData.getFirstName());
		user.setLastName(reqData.getLastName());
		return new BooleanResponseData(dbManager.editInDataBase(user.getId(), DBEntityType.User, user));
	}

	public ResponseData EditContactsList(EditContactsListRequestData reqData,User user) {
		ArrayList<Contact> currentContactsList = dbManager.getContactsList(user.getId());
		if (currentContactsList == null || currentContactsList.size() == 0)
			return new BooleanResponseData(false);
		if (reqData.getUpdatedFriendsList().size() == 0)
			return new BooleanResponseData(false);
		currentContactsList.forEach(contact -> {
			if (!reqData.getUpdatedFriendsList().contains("" + contact.getFriend().getEmail())) {
				dbManager.deleteFromDataBase(contact.getId(), DBEntityType.Contact);
			}
		});

		return new BooleanResponseData(true);
	}

	public ResponseData ChangePassword(ChangePasswordRequestData reqData,User user) {
		Credential credential = dbManager.getCredential(user.getId());
		if (credential == null)
			return new ErrorResponseData(ErrorType.TechnicalError);

		if (!credential.getCredntial().equals(reqData.getOldPassword()))
			return new ErrorResponseData(ErrorType.WrongPreviousPassword);

		if (reqData.getNewPassword().equals(reqData.getOldPassword()))
			return new ErrorResponseData(ErrorType.BothPasswordsEquals);
		credential.setCredntial(reqData.getNewPassword());
		Boolean res = dbManager.editInDataBase(credential.getId(), DBEntityType.Credential, credential);
		return res ? new BooleanResponseData(true) : new ErrorResponseData(ErrorType.TechnicalError);
	}

	public ResponseData AddFriend(AddFriendRequestData reqData,User user) {
		User friend = dbManager.getUser(reqData.getFriendMail());
		if (friend == null)
			return new ErrorResponseData(ErrorType.FriendIsNotExist);
		else if (user.getId() == friend.getId())
			return new ErrorResponseData(ErrorType.BothUsersEquals);
		else {
			if (dbManager.getContact(user.getId(), friend.getId()) == null) {
				dbManager.addToDataBase(new Contact(user, friend));
				return new AddFriendResponseData(dbManager.getUserDataFromDBUserEntity(friend));
			} else
				return new ErrorResponseData(ErrorType.AlreadyFriends);
		}

	}

	public ResponseData EventsList(EventsListRequestData reqData,User user) {
		LinkedList<EventData> eventsList = dbManager.getEventsList(user.getId());
		if (eventsList == null)
			return new ErrorResponseData(ErrorType.UserHasNoEvents);
		return new EventsListResponseData(eventsList);
	}
	
	public ResponseData DeclineEvent(DeclineEventRequestData reqData,User user) {
		UserEvent ue = dbManager.getSpecificUserEvent(user.getId(), reqData.getEventId());
		if (ue == null)
			return new ErrorResponseData(ErrorType.NoPendingEvents);
		if (ue.getAnswer() == 0) {
			ue.setAnswer(2);
			dbManager.editInDataBase(ue.getId(), DBEntityType.UserEvent, ue);
		}
		return new BooleanResponseData(true);
	}

	public ResponseData LeaveEvent(LeaveEventRequestData reqData,User user) {
		Event e = (Event) dbManager.get(reqData.getEventId(), DBEntityType.Event);
		if (e == null)
			return new ErrorResponseData(ErrorType.TechnicalError);
		if (e.getIsFinished() == 0) {
			UserEvent ue = dbManager.getSpecificUserEvent(user.getId(), reqData.getEventId());
			if (ue == null)
				return new ErrorResponseData(ErrorType.NoPendingEvents);
			else if (ue.getAnswer() == 1)
			{
				LinkedList<UserData> list = getParticipantsUserData(e);
				UserData ud = dbManager.getUserDataFromDBUserEntity(user);
				socketHandler.sendUserEventNotification(dbManager.getEventDataByEvent(e, list),list,ud,false);
			}
		}
		return new BooleanResponseData(true);
	}

	public ResponseData JoinEvent(ConfirmEventRequestData reqData,User user) {
		UserEvent ue = dbManager.getSpecificUserEvent(user.getId(), reqData.getEventId());
		if (ue == null)
			return new ErrorResponseData(ErrorType.NoPendingEvents);
		if (ue.getAnswer() == 0 || ue.getAnswer() == 2) {
			ue.setAnswer(1);
			dbManager.editInDataBase(ue.getId(), DBEntityType.UserEvent, ue);
		}
		LinkedList<UserData> list = getParticipantsUserData(ue.getEvent());
		UserData ud = dbManager.getUserDataFromDBUserEntity(user);
		socketHandler.sendUserEventNotification(dbManager.getEventDataByEvent(ue.getEvent(), list),list,ud,true);
		return new BooleanResponseData(true);
	}
	
	public void CloseEvent(int eventId,byte[] bytes) {
		//Update Event in DB
		Event event = (Event) dbManager.get(eventId, DBEntityType.Event);
		event.setIsFinished(1);
		dbManager.editInDataBase(eventId, DBEntityType.Event, event);
		//Send Notifications
		ArrayList<UserEvent> participants = dbManager.getParticipants(eventId);
		ArrayList<UserData> usersDataList = new ArrayList<>();
		if(participants != null)
		{
			participants.forEach(p->{
				usersDataList.add(dbManager.getUserDataFromDBUserEntity(p.getUser()));
			});
			socketHandler.sendEventCloseNotificationToUsers(dbManager.getEventDataByEvent(event, usersDataList), usersDataList);
		}
		//Send To Recognize
		if(bytes != null && participants != null)
		{
			ArrayList<String> participantsEmails = new ArrayList<>();
			participants.forEach(p -> {
				participantsEmails.add(p.getUser().getEmail());
			});
			Thread t = new Thread(new Runnable() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					ArrayList<ProtocolLine> protocolLines = recognizeManager.SendWavToRecognize(bytes, participantsEmails,eventId);
					protocolLines.forEach(pl->{
						System.out.println(pl.toString());
					});
					if(protocolLines != null)
						saveNewProtocol(protocolLines, event, usersDataList);
				}
			});
			t.start();
		}
	}

	public ResponseData UpdateProfilePicture(UpdateProfilePictureRequestData reqData,User user) {
		byte[] bytes = reqData.getProfilePictureBytes();
		if(bytes == null)
			return new ErrorResponseData(ErrorType.TechnicalError);
		return filesHandler.SaveByteArrayInDestinationAsImage(bytes, "jpg", user.getId()+".jpg") ? new BooleanResponseData(true) : new ErrorResponseData(ErrorType.TechnicalError);
		
	}
	
	public void DataSet(int userId, int length, byte[] bytes)
	{
		Path path = Paths.get(this.path+"testGal.wav");
		try {
			Files.write(path, bytes);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		DataSet ds = dbManager.getDataSetByUserId(userId);
		if(ds == null)
		{
			ds = new DB.DataSet((User)dbManager.get(userId, DBEntityType.User), 0);
			ds.setId(dbManager.addToDataBase(ds));
		}
		float newLen =ds.getLengthOfRecorcds()+ length/60 + (length%60)/60;
		System.out.println(newLen);
		ds.setLengthOfRecorcds(newLen);
		dbManager.editInDataBase(ds.getId(), DBEntityType.DataSet, ds);
		recognizeManager.CreateDataSet(bytes, ds.getUser().getEmail());
	}
	
	

	public static boolean isEmailsEquals(String mail1, String mail2) {
		
		return (mail1 == null || mail2 == null) ? false : mail1.toLowerCase().equals(mail2.toLowerCase());
	}
	
	private LinkedList<UserData> getParticipantsUserData(Event e)
	{
		LinkedList<UserData> list = new LinkedList<>();
		ArrayList<UserEvent> ueList = dbManager.getParticipants(e.getId());
		ueList.forEach(uel -> {
			list.add(dbManager.getUserDataFromDBUserEntity(uel.getUser()));
		});
		return list;
	}
	
	private void saveNewProtocol(ArrayList<ProtocolLine> protocol,Event event,List<UserData> list)
	{
		filesHandler.fromProtocolToTextFile(protocol, event.getId()+".txt");
		event.setIsConverted(1);
		dbManager.editInDataBase(event.getId(), DBEntityType.Event, event);
		//notify
		socketHandler.sendProtocolIsReadyNotification(dbManager.getEventDataByEvent(event, list), list);
	}

	public void checkAndSendInvitesAfterLogin(SocketIOClient client,User u)
	{
		ArrayList<UserEvent> ue = dbManager.getUserEventsWithSpecificAnswer(u.getId(),0);
		if(ue != null)
		{
			ue.forEach(invite -> {
				ArrayList<UserEvent> usersList = dbManager.getParticipants(invite.getEvent().getId());
				if(usersList != null)
				{
					ArrayList<UserData> udList = new ArrayList<>();
					usersList.forEach(userEvent->{
						udList.add(dbManager.getUserDataFromDBUserEntity(userEvent.getUser()));
					});
					socketHandler.sendToClient(client, "Notification", new EventInvitationNotificationData(dbManager.getEventDataByEvent(invite.getEvent(), udList)));
				}
			});
		}
	}
}
