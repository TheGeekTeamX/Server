package MVC;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.apache.commons.codec.binary.Base64;

import ResponsesEntitys.ProtocolLine;

public class FilesHandler {
	private String path;
	
	
	
	
	
	public FilesHandler(String path) {
		super();
		this.path = path.contains("_") ? path.replace("_", " ") : path;
	}

	public byte[] FromImageToByteArray(String name,String format)
	{
		try {
			BufferedImage im = ImageIO.read(new File(path+"Images\\"+name));
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(im, "jpg", baos);
			baos.flush();
			return baos.toByteArray();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

	}
	
	public Boolean SaveByteArrayInDestinationAsImage(byte[] arr,String format,String dest)
	{
	
		ByteArrayInputStream in = new ByteArrayInputStream(arr);
		try {
			BufferedImage bi = ImageIO.read(in);
			//BufferedImage bi = ImageIO.read(getClass().getResource(dest));
			ImageIO.write(bi, format, new File(path+"Images/"+dest));
			//ImageIO.write(bi, format, new File("./ResourcesDirectory/Images/1test.jpg"));//Only in eclipse

			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}
	
	public ArrayList<ProtocolLine> fromTextFileToProtocol(String name)
	{
		ArrayList<ProtocolLine> list = new ArrayList<>();
		Scanner sc = null;
			try {
				sc = new Scanner(new File(path+"Protocols/"+name));
				sc.useDelimiter("\\,");
				while(sc.hasNext())
					{
						String line = sc.next();
						String[] strArr = line.split(":");
						if(strArr.length == 2)
						{
							list.add(new ProtocolLine(strArr[0],strArr[1]));
						}
					}
				
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			finally {
				//sc.close();
			}
			return list;
	}
	
	public void fromProtocolToTextFile(ArrayList<ProtocolLine> list,String dest)
	{
		FileWriter fw = null;
		BufferedWriter bw =null;
		try{
			fw = new FileWriter(new File(path+"Protocols/"+dest));
			bw = new BufferedWriter(fw);
			for(int i = 0; i<list.size();i++)
			{
				bw.write(""+list.get(i).getName()+":"+list.get(i).getText()+",");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				bw.close();
				fw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}//
		}
		
		
	}

	public String FromWavToString(String path) {
		try {
			Path fileLocation = Paths.get(path);
			byte[] data = Files.readAllBytes(fileLocation);
			AudioInputStream audioIn = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
			AudioInputStream ais = new AudioInputStream(audioIn, audioIn.getFormat(), audioIn.getFrameLength());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
			byte[] audioBytes = out.toByteArray();
			String splittedWav = Base64.encodeBase64String(audioBytes);
			out.close();
			return splittedWav;
		}  catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	

}
