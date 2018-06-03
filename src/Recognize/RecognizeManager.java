package Recognize;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import ResponsesEntitys.ProtocolLine;
import org.apache.commons.codec.binary.Base64;

public class RecognizeManager {
	private String path;
	private static String RecognizeURL = "http://193.106.55.106:5000/create_model_user";
	private static String RegisterURL = "http://193.106.55.106:5000/create_dataset";
	private static String PredictURL = "http://193.106.55.106:5000/predict";

	
	
	public RecognizeManager(String path) {
		super();
		this.path = path;
	}

	// Send identical record from user to Recognize Service
	public boolean CreateDataSet(byte[] wavByte, String user) {
		// Divide Wav file to chunks of 4 seconds
		WavSplitFixedTime ws = new WavSplitFixedTime(wavByte, 4);
		List<String> list = ws.getList();
		for (int i = 0; i < list.size(); i++) {
			ArrayList<NameValuePair> postParameters;
			postParameters = new ArrayList<NameValuePair>();
			postParameters.add(new BasicNameValuePair("label", user));
			postParameters.add(new BasicNameValuePair("file", list.get(i)));
			JSONObject result = CreatePost(RegisterURL, postParameters);
			String usersRecognize = result.getString("result");

		}
		ArrayList<NameValuePair> postParameters2 = new ArrayList<NameValuePair>();
		postParameters2.add(new BasicNameValuePair("label", user));
		JSONObject result = CreatePost(RecognizeURL, postParameters2);
		if (result == null) {
			System.out.println("Failed to send post request to :" + RegisterURL);
			return false;
		}
		return true;

	}

	// Send event details to recognize server on event open (Not in use)
	/*
	 * public boolean onEventOpen(String URL, String eventId, List<String> users) {
	 * 
	 * ArrayList<NameValuePair> postParameters; postParameters = new
	 * ArrayList<NameValuePair>(); postParameters.add(new
	 * BasicNameValuePair("meet_id", eventId)); postParameters.add(new
	 * BasicNameValuePair("label", label)); boolean result = CreatePost(URL,
	 * postParameters); if (!result) {
	 * System.out.println("Failed to send post request to :" + URL); return false; }
	 * return true;
	 * 
	 * }
	 */

	// Send the Wav to recognize service
	public ArrayList<ProtocolLine> SendWavToRecognize(byte[] wavByte, List<String> usersList,int eventId) {
		String usersListStr = "";
		for (int i = 0; i < usersList.size(); i++)
			usersListStr += usersList.get(i) + ",";
		usersListStr = usersListStr.substring(0, usersListStr.length());
		ArrayList<ProtocolLine> pl = new ArrayList<>();

		// Divide Wav file to chunks of 2 seconds (not in use)
		// WavSplitFixedTime ws = new WavSplitFixedTime(wavByte, 8);
		// List<String> list = ws.getList();

		List<String> list = getWavList(wavByte,eventId);
		ArrayList<NameValuePair> postParameters;
		postParameters = new ArrayList<NameValuePair>();
		postParameters.add(new BasicNameValuePair("list_users", usersListStr));

		StringBuilder sb = new StringBuilder();
		for(int i=0;i<list.size();i++)
			sb.append(list.get(i)+",");
		sb.deleteCharAt(sb.length() - 1);
		postParameters.add(new BasicNameValuePair("audio_parts", sb.toString()));

		JSONObject result = CreatePost(PredictURL, postParameters);
		String usersRecognize = result.getString("result");
		List<String> usersListRecognize = new LinkedList<String>(Arrays.asList(usersRecognize.split(",")));

		pl = BuildProtocol(list, usersListRecognize);
		System.out.println(eventId+" Protocol Is Ready");

		return pl;
	}

	// Comparing between list of users from recognize service and regular record
	private ArrayList<ProtocolLine> BuildProtocol(List<String> wavBytes, List<String> usersList) {
		int startIndex = 0, endIndex = 0;
		String text;
		ArrayList<ProtocolLine> pl = new ArrayList<>();

		String currentUser = usersList.size() == 0 ? "" : usersList.get(0);
		byte[] mergedBytes = null;
		System.out.println(wavBytes.size());
		for (int i = 0; i < wavBytes.size(); i++) {
			if (i + 1 == usersList.size() || !currentUser.equals(usersList.get(i))) {
				mergedBytes = MergeWavList(wavBytes.subList(startIndex, endIndex + 1), "" + startIndex);
				try {
					text = TranslateWithGoogleService(mergedBytes);
					pl.add(new ProtocolLine(currentUser, text));
					startIndex = i;
					endIndex = i;
					currentUser = usersList.get(i);

					if (i == wavBytes.size() - 1) {
						mergedBytes = MergeWavList(wavBytes.subList(endIndex, usersList.size()), "" + startIndex);
						text = TranslateWithGoogleService(mergedBytes);
						pl.add(new ProtocolLine(currentUser, text));
					}

				} catch (Exception e) {
					System.out.println(e.getMessage());
					e.printStackTrace();
				}
			} else
				endIndex = i;
		}
		pl.forEach(p->{
			if(p.getText() == null)
				p.setText("...");
		});
		return pl;

	}

	private byte[] MergeWavList(List<String> wavBytes, String user) {
		AudioInputStream audio2;
		byte[] mergedBytes = null;
		try {
			File fileOut = File.createTempFile("tempFile", "wav");
			AudioInputStream audioBuild = AudioSystem
					.getAudioInputStream(new ByteArrayInputStream(Base64.decodeBase64(wavBytes.get(0).getBytes())));
			for (int i = 1; i < wavBytes.size(); i++) {
				audio2 = AudioSystem
						.getAudioInputStream(new ByteArrayInputStream(Base64.decodeBase64(wavBytes.get(i).getBytes())));
				audioBuild = new AudioInputStream(new SequenceInputStream(audioBuild, audio2), audioBuild.getFormat(),
						audioBuild.getFrameLength() + audio2.getFrameLength());
			}

			AudioSystem.write(audioBuild, AudioFileFormat.Type.WAVE, fileOut);
			Path path = Paths.get(fileOut.getPath());
			mergedBytes = Files.readAllBytes(path);
			Files.delete(path);
		} catch (IOException | UnsupportedAudioFileException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		return mergedBytes;
	}

	// Set the record to GoogleSpeechToText
	private String TranslateWithGoogleService(byte[] wavByte) throws Exception {
		String res;
		SpeechToText st = new SpeechToText(this.path);
		res = st.getConvertText(wavByte);
		return res;
	}

	// Send the wav file to get split voices
	@SuppressWarnings("unchecked")
	private List<String> getWavList(byte[] wavFile,int eventId) {
		String root = System.getProperty("user.dir");
		String python = "python C:\\Users\\project06\\Desktop\\pydub_splitter.py";
		Path rootDirectory = FileSystems.getDefault().getPath(path+"Temp");
		Path tempDirectory;
		Path tempFile;
		
		try {
			// Create temp directory
			tempDirectory = Files.createTempDirectory(rootDirectory, ""+eventId+"-");


			// Create temp files from python
			tempFile = Files.createTempFile(tempDirectory,  ""+eventId+"-","");

			
			AudioInputStream audioBuild = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavFile));
			AudioSystem.write(audioBuild, AudioFileFormat.Type.WAVE, tempFile.toFile());

			// Python
			@SuppressWarnings("unused")
			Process p = Runtime.getRuntime().exec(python + " " + tempFile + " " + tempDirectory + "/");
			p.waitFor();

			// Read wav list from temp folder
			List<String> wavList = new ArrayList<>();
			Files.delete(tempFile);
			File dir = new File(tempDirectory.toUri());
			File[] directoryListing = dir.listFiles();

			if (directoryListing != null) {
				Arrays.sort(directoryListing,0, directoryListing.length,  new Comparator<File>()
				{
				    @Override
				    public int compare(File f1, File f2) {
				        String fileName1 = f1.getName();
				        String fileName2 = f2.getName();
				        String[] a1 = fileName1.split("-");
				        String[] a2 = fileName2.split("-");
				        int i1 = Integer.parseInt(a1[0]);
				        int i2 = Integer.parseInt(a2[0]);
				        return i1-i2;
				    }
				});
				for (File wavChild : directoryListing) {
					System.out.println(wavChild.getName());
					Path path = Paths.get(wavChild.getPath());
					byte[] mergedBytes = Files.readAllBytes(path);
					wavList.add(Base64.encodeBase64String(mergedBytes));
					Files.delete(path);

				}
			}
			Files.delete(tempDirectory);
			if(wavList.size() == 0)
			{
				System.out.println("2 Seconds Logic");
				WavSplitFixedTime ws = new WavSplitFixedTime(wavFile, 2);
				wavList = ws.getList();
			}
			else
				System.out.println("Voice Splitter Logic");
			return wavList;
			//Files.delete(tempDirectory);
		} catch (IOException | UnsupportedAudioFileException  e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		return null;
	}

	private JSONObject CreatePost(String URL, ArrayList<NameValuePair> postParameters) {
		HttpClient httpclient = HttpClientBuilder.create().build();
		HttpPost post = new HttpPost(URL);
		try {
			post.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));
			ResponseHandler<String> responseHandler = new BasicResponseHandler();
			String responseBody;
			responseBody = httpclient.execute(post, responseHandler);
			JSONObject response = new JSONObject(responseBody);
			return response;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

}