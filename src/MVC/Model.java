package MVC;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import DB.*;
import Enums.DBEntityType;
import Enums.ErrorType;
import Recognize.RecognizeManager;
import Requests.*;
import Responses.*;
import ResponsesEntitys.EventData;
import ResponsesEntitys.ProtocolLine;
import ResponsesEntitys.UserData;
import Tools.BytesHandler;

public class Model extends Observable {
	private static Model instance;
	private DBManager dbManager;
	private SocketHandler socketHandler;
	private RecognizeManager recognizeManager;
	
	
	public SocketHandler getSocketHandler() {
		return socketHandler;
	}

	public void setSocketHandler(SocketHandler socketHandler) {
		this.socketHandler = socketHandler;
	}

	public EventData getEventData(Event e,List<UserData> udList)
	{
		return dbManager.getEventDataByEvent(e, udList);
	}
	
	public User getUser(String email)
	{
		return dbManager.getUser(email);
	}
	

	public static Model getInstance()
	{
		if(instance == null)
		{
			instance = new Model(DBManager.getInstance());
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
	public Model(DBManager dbm) {
		super();
		this.dbManager = dbm;
		recognizeManager = new RecognizeManager();
	}

	public ResponseData EventProtocol(EventProtocolRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send EventProtocolRequest");
		String protocolName = dbManager.getRelatedEventProtocol(reqData.getEventID());
		if (protocolName == null || protocolName.equals(""))
			return new ErrorResponseData(ErrorType.ProtocolIsNotExist);
		ArrayList<ProtocolLine> protocol = BytesHandler
				.fromTextFileToProtocol(protocolName);
		return protocol != null ? new EventProtocolResponseData(reqData.getEventID(), protocol)
				: new ErrorResponseData(ErrorType.TechnicalError);
	}

	public ResponseData CreateEvent(CreateEventRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send CreateEventRequest");
		ArrayList<String> participantsEmail = (ArrayList<String>) (reqData.getUsersEmails());
		LinkedList<User> participants = new LinkedList<>();
		participantsEmail.forEach(pe -> {
			User u = dbManager.getUser(pe);
			if (u != null)
				participants.add(u);
		});
		participants.add(user);
		// create Event
		Event e = new Event(user, reqData.getTitle(), new Date(Calendar.getInstance().getTime().getTime()).toString(),
				0, 0, reqData.getDescription());
		if (!(dbManager.addToDataBase(e) > 0))
			return new ErrorResponseData(ErrorType.TechnicalError);
		// create UserEvent
		LinkedList<UserData> participantsUserData = getParticipantsUserData(e);
		participants.forEach(p -> {
			int answer = isEmailsEquals(p.getEmail(), user.getEmail()) ? 1 : 0;
			dbManager.addToDataBase(new UserEvent(p, e, answer));
		});
		// send invites
		socketHandler.sendEventInventationToUsers(getEventData(e, participantsUserData), participantsUserData);
		return new BooleanResponseData(true);
	}
	
	public ResponseData IsUserExist(IsUserExistRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send IsUserExistRequest");
		User otherUser = dbManager.getUser(reqData.getEmail());
		if (otherUser == null)
			return new ErrorResponseData(ErrorType.FriendIsNotExist);
		return new IsUserExistResponseData(dbManager.getUserDataFromDBUserEntity(otherUser));
	}
	
	public ResponseData ProfilePicture(ProfilePictureRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send ProfilePictureRequest");
		ProfilePicture pp = dbManager.getUserProfilePicture(user.getId());
		if (pp == null)
			return new ErrorResponseData(ErrorType.UserHasNoProfilePicture);
		else
		{
			byte[] arr = BytesHandler.FromImageToByteArray(pp.getProfilePictureUrl(),"jpg");
			if(arr != null && arr.length > 0)
				return new ProfilePictureResponseData(arr);
			return new ErrorResponseData(ErrorType.TechnicalError);
		}
	}
	
	public ResponseData CreateUser(CreateUserRequestData reqData) {
		notifyObservers(reqData.getUserEmail() + " Send CreateUserRequest");
		User user = dbManager.getUser(reqData.getUserEmail());
		if(user == null)
		{			
			User u = new User(reqData.getUserEmail(), reqData.getFirstName(), reqData.getLastName(),reqData.getPhoneNumber(), reqData.getCountry());
			u.setId(dbManager.addToDataBase(u));
			if (u.getId() < 0)
				return new ErrorResponseData(ErrorType.TechnicalError);
			byte[] bytes = reqData.getImageBytes();
			if(bytes != null)
				BytesHandler.SaveByteArrayInDestinationAsImage(bytes, "jpg", "/Images/"+u.getId()+".jpg");
			if(dbManager.addToDataBase(new Credential(u, reqData.getCredential())) < 0)
				return new ErrorResponseData(ErrorType.TechnicalError);
			return new BooleanResponseData(true);
		}
		else
			return new ErrorResponseData(ErrorType.EmailAlreadyRegistered);
	}

	public ResponseData ContactList(ContactsListRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send ContactListRequest");
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
		notifyObservers(reqData.getUserEmail() + " Send EditUserRequest");
		user.setCountry(reqData.getCountry());
		user.setPhoneNumber(reqData.getPhoneNumber());
		user.setFirstName(reqData.getFirstName());
		user.setLastName(reqData.getLastName());
		return new BooleanResponseData(dbManager.editInDataBase(user.getId(), DBEntityType.User, user));
	}

	public ResponseData EditContactsList(EditContactsListRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send EditContactsListRequest");
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
		notifyObservers(reqData.getUserEmail() + " Send ChangePasswordRequest");
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
		notifyObservers(reqData.getUserEmail() + " Send AddFriendRequest");
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
		notifyObservers(reqData.getUserEmail() + " Send EventListRequest");
		LinkedList<EventData> eventsList = dbManager.getEventsList(user.getId());
		if (eventsList == null)
			return new ErrorResponseData(ErrorType.UserHasNoEvents);
		return new EventsListResponseData(eventsList);
	}
	
	public ResponseData DeclineEvent(DeclineEventRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send DeclineEventRequest");
		UserEvent ue = dbManager.getRelatedUserEvent(user.getId(), reqData.getEventId());
		if (ue == null)
			return new ErrorResponseData(ErrorType.NoPendingEvents);
		if (ue.getAnswer() == 0) {
			ue.setAnswer(2);
			dbManager.editInDataBase(ue.getId(), DBEntityType.UserEvent, ue);
		}
		return new BooleanResponseData(true);
	}

	public ResponseData LeaveEvent(LeaveEventRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send LeaveEventRequest");
		Event e = (Event) dbManager.get(reqData.getEventId(), DBEntityType.Event);
		if (e == null)
			return new ErrorResponseData(ErrorType.TechnicalError);
		if (e.getIsFinished() == 0) {
			UserEvent ue = dbManager.getRelatedUserEvent(user.getId(), reqData.getEventId());
			if (ue == null)
				return new ErrorResponseData(ErrorType.NoPendingEvents);
			else if (ue.getAnswer() == 1)
			{
				LinkedList<UserData> list = getParticipantsUserData(e);
				UserData ud = dbManager.getUserDataFromDBUserEntity(user);
				if(list.contains(ud))
					list.remove(ud);
				socketHandler.sendUserEventNotification(dbManager.getEventDataByEvent(e, list),list,ud,false);
			}
		}
		return new BooleanResponseData(true);
	}

	public ResponseData JoinEvent(ConfirmEventRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send ConfirmEventRequest");
		UserEvent ue = dbManager.getRelatedUserEvent(user.getId(), reqData.getEventId());
		if (ue == null)
			return new ErrorResponseData(ErrorType.NoPendingEvents);
		if (ue.getAnswer() == 0 || ue.getAnswer() == 2) {
			ue.setAnswer(1);
			dbManager.editInDataBase(ue.getId(), DBEntityType.UserEvent, ue);
		}
		LinkedList<UserData> list = getParticipantsUserData(ue.getEvent());
		UserData ud = dbManager.getUserDataFromDBUserEntity(user);
		if(list.contains(ud))
			list.remove(ud);
		socketHandler.sendUserEventNotification(dbManager.getEventDataByEvent(ue.getEvent(), list),list,ud,true);
		return new BooleanResponseData(true);
	}
	
	public ResponseData CloseEvent(CloseEventRequestData reqData,User user) {
		notifyObservers(reqData.getUserEmail() + " Send CloseEventRequest");
		Event event = (Event) dbManager.get(reqData.getEventId(), DBEntityType.Event);
		if (event == null)
			return new ErrorResponseData(ErrorType.EventIsNotExist);
		if (event.getAdmin().getId() == user.getId()) {
			event.setIsFinished(1);
			ArrayList<UserEvent> usersEvent = dbManager.getUserEventByEventId(event.getId());
			usersEvent.forEach(ue -> {
				if (ue.getAnswer() == 0)// didn't answer yet
				{
					ue.setAnswer(2);
					dbManager.editInDataBase(ue.getId(), DBEntityType.UserEvent, ue);
				}
			});
			if (dbManager.editInDataBase(event.getId(), DBEntityType.Event, event))
				return new ErrorResponseData(ErrorType.TechnicalError);
			else {
				LinkedList<UserData> list = getParticipantsUserData(event);
				UserData ud = dbManager.getUserDataFromDBUserEntity(user);
				if(list.contains(ud))
					list.remove(ud);
				socketHandler.sendEventCloseNotificationToUsers(dbManager.getEventDataByEvent(event, list),list);
				byte[] bytes = reqData.getRecordsBytes();
				
				LinkedList<String> usersEmailsString = new LinkedList<>();
				list.forEach(l -> {
					usersEmailsString.add(l.getEmail());
				});
				Thread t = new Thread(new Runnable() {
					
					@Override
					public void run() {
						// TODO Auto-generated method stub
						saveNewProtocol(recognizeManager.SendWavToRecognize(bytes, usersEmailsString),event,list);
						
					}
				});
				t.start();
				return new BooleanResponseData(true);
			}
		} else
			return new ErrorResponseData(ErrorType.UserIsNotAdmin);
	}

	public ResponseData UpdateProfilePicture(UpdateProfilePictureRequestData reqData,User user) {
		byte[] bytes = reqData.getProfilePictureBytes();
		ProfilePicture pp = dbManager.getUserProfilePicture(user.getId());
		if(pp == null)
		{
			pp = new ProfilePicture(user, "/Images/"+user.getId()+".jpg");
			if(dbManager.addToDataBase(pp) < 0)
				return new ErrorResponseData(ErrorType.TechnicalError);
		}
		if(bytes == null)
			return new ErrorResponseData(ErrorType.TechnicalError);
		return BytesHandler.SaveByteArrayInDestinationAsImage(bytes, "jpg", pp.getProfilePictureUrl()) ? new BooleanResponseData(true) : new ErrorResponseData(ErrorType.TechnicalError);
		
	}
	
	public ResponseData DataSet(DataSetRequestData reqData,User user)
	{
		if(reqData.getRecord() == null)
			return new ErrorResponseData(ErrorType.TechnicalError);
		DataSet ds = dbManager.getDataSetByUserId(user.getId());
		if(ds == null)
		{			
			ds = new DataSet(user, 0);
			dbManager.addToDataBase(ds);
			ds = dbManager.getDataSetByUserId(user.getId());
		}
		Thread t = new Thread(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				recognizeManager.CreateDataSet(reqData.getRecord(), user.getEmail());
			}
		});
		t.start();
		ds.setLengthOfRecorcds(ds.getLengthOfRecorcds() + reqData.getLength());
		dbManager.editInDataBase(ds.getId(), DBEntityType.DataSet, ds);
		return new DataSetResponseData(ds.getLengthOfRecorcds());
	}

	public static boolean isEmailsEquals(String mail1, String mail2) {
		
		return (mail1 == null || mail2 == null) ? false : mail1.toLowerCase().equals(mail2.toLowerCase());
	}
	
	private LinkedList<UserData> getParticipantsUserData(Event e)
	{
		LinkedList<UserData> list = new LinkedList<>();
		ArrayList<UserEvent> ueList = dbManager.getUserEventByEventId(e.getId());
		ueList.forEach(uel -> {
			list.add(dbManager.getUserDataFromDBUserEntity(uel.getUser()));
		});
		return list;
	}
	
	private void saveNewProtocol(ArrayList<ProtocolLine> protocol,Event event,LinkedList<UserData> list)
	{
		BytesHandler.fromProtocolToTextFile(protocol, "/Protocols/"+event.getId()+".txt");
		event.setIsConverted(1);
		dbManager.editInDataBase(event.getId(), DBEntityType.Event, event);
		//notify
		socketHandler.sendProtocolIsReadyNotification(dbManager.getEventDataByEvent(event, list), list);
	}
}