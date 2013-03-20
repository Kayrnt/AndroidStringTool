package com.kayrnt.android.stringstool;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import com.kayrnt.android.stringstool.model.AndroidStringRessource;
import com.kayrnt.android.stringstool.model.ResourcesElement;
import com.kayrnt.android.stringstool.model.StringElement;
import com.kayrnt.android.stringstool.utils.ParserUtils;


public class Main {

	static ArrayList<AndroidStringRessource> stringsFile;
	static Marshaller marshaller;
	final static ArrayList<Thread> threads = new ArrayList<Thread>();

	public static void main(String[] args)
	{
		stringsFile = new ArrayList<AndroidStringRessource>();
		File file = new File("res");
		try
		{
			long start = System.currentTimeMillis();
			visitAllFiles(file, null);
			syncStrings();
			closeAndroidXMLStrings();
			System.out.println("time consumed :"+(System.currentTimeMillis()-start));
		}
		catch(Exception e){
			System.out.print("exception : "+e.getCause().getMessage());
		}
	}

	//Parse file and init our structures
	private static void readFile(File file, String parent) throws Exception
	{

		//System.out.println("file : "+file.exists());
		//System.out.println("file : "+file.getAbsolutePath());

		ResourcesElement adr = new ResourcesElement();

		String fileTransformed = ParserUtils.getString(file);
		//System.out.println(fileTransformed);
		adr = parse(fileTransformed,ResourcesElement.class);
		//System.out.println("res size : "+adr.string.size());
		stringsFile.add(new AndroidStringRessource(file,adr));
		//		for(int i = 0; i < adr.string.size(); i++) {
		//			System.out.println(adr.string.get(i));
		//		}

	}

	private static <T> T parse(String str, Class<T> classToInstantiate) throws JAXBException {
		System.out.println("parsing...");
		Unmarshaller unmarshaller = JAXBContext.newInstance(classToInstantiate).createUnmarshaller();
		System.out.println("unmarshaller...");
		return classToInstantiate.cast(unmarshaller.unmarshal(new StringReader(str)));
	}


	// Process only files under dir
	private static void visitAllFiles(File dir, String parent) throws Exception {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i= 0; i<children.length; i++) {
				visitAllFiles(new File(dir, children[i]), dir.getName());
			}
		} else {
			readStrings(dir, parent);
		}
	}

	private static void readStrings(File file, String parent) throws Exception {
		if(file.getName().equals("strings.xml")) {
			readFile(file, parent);
		}
	}

	private static void syncStrings() {
		System.out.println("number of standard XML : "+stringsFile.size());
		// finding the standard values
		AndroidStringRessource standardXML = null;
		for(int i = 0; i < stringsFile.size(); i++) {
			AndroidStringRessource current = stringsFile.get(i);
			if(current.getFile().getAbsolutePath().endsWith("res/values/strings.xml")) {
				//reference file
				standardXML = current;
			}
		}

		//if null print error and stop
		if(standardXML == null) {
			System.out.println("No standard strings.xml found in res/values/ of this project");
			System.out.println("The program found : "+stringsFile.size()+" values files.");
			for(int i = 0; i < stringsFile.size(); i++) {
				System.out.println("path for element "+i+" is "+stringsFile.get(i).getFile().getAbsolutePath());
			}
		} else {
			System.out.println("default xml strings found...");
		}

		//preparing the marshaller for string elements 
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(StringElement.class);
			marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, new Boolean(true));
		} catch (JAXBException e) {
			e.printStackTrace();
		}

		//Removing the standardXML from list that is going
		stringsFile.remove(standardXML);

		//preparing string element list
		List<StringElement> standardStrings = standardXML.getResources().string;

		//init the vectors to this size
		int standardStringsSize = standardStrings.size();
		System.out.println("strings : "+standardStrings.size());
		for(int i = 0; i < stringsFile.size(); i++) {
			AndroidStringRessource current = stringsFile.get(i);
			current.setParts(new String[standardStringsSize]);
		}


		//Now iterating on its values

		ExecutorService pool;
		pool = Executors.newFixedThreadPool(6);
//		ListeningExecutorService service = MoreExecutors.listeningDecorator(pool);
		for(int i = 0; i < standardStrings.size(); i++) {
			final StringElement currentElement = standardStrings.get(i);
			pool.execute(new ComparingHandler(currentElement, i));
//			threads.add(currentThread);
//			currentThread.start();
		}

		ArrayList<Callable<Void>> callables = new ArrayList<Callable<Void>>();
		callables.add(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				Main.mergeStrings();
				Main.closeAndroidXMLStrings();
				return null;
			}
		});
		
		try {
			pool.invokeAll(callables);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		

	}

	static void checkElementIsInOtherStringsXML(StringElement stringElement, int position) {
		for(int i = 0; i < stringsFile.size(); i++) {
			List<StringElement> currentList = stringsFile.get(i).getResources().string;
			String[] parts = stringsFile.get(i).getParts();
			//It's a comment so we just rename it and print it properly
			if(stringElement.name.startsWith("__comment_")) {
				parts[position] = "<!-- "+stringElement.text+" -->\n";
				System.out.println("comment found !");
			} else {
				//lets search it...
				boolean found = false;
				for(int j = 0; j < currentList.size(); j++) {
					StringElement currentString = currentList.get(j);
					//if found we print and break
					if(currentString.name.equals(stringElement.name)) {
						found = true;
						parts[position] = "<string name=\""+currentString.name+"\">"+currentString.text+"</string>\n";
						//System.out.println("postion :"+parts[position]);
//						synchronized (stringsFile) {
//							currentList.remove(j);
//						}
						break;
					}
				}
				//else we print as empty
				if(!found){
					parts[position] = "<string name=\""+stringElement.name+"\"></string>\n";
				}
			}
		}
	}

	static void mergeStrings() {
		for(int i = 0; i < stringsFile.size(); i++) {
			AndroidStringRessource current = stringsFile.get(i);
			//StringBuilder builder = new StringBuilder();
			String finalString = "";
			String[] parts = current.getParts();
			for(int j = 0; j < parts.length; j++) {
				//builder.append(parts[j]);
				finalString += parts[j];
			}
			//			current.setTransformed(builder.toString());
			current.setTransformed(finalString);
		}
	}


	static void closeAndroidXMLStrings() {
		//close the strings resources and write
		for(int i = 0; i < stringsFile.size(); i++) {
			//append xml base
			stringsFile.get(i).setTransformed("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n"+stringsFile.get(i).getTransformed()+"</resources>");
			//System.out.println(stringsFile.get(i).getTransformed());
			// file
			try{
				// Create file 
				FileWriter fstream = new FileWriter(stringsFile.get(i).getFile().getParent()+"/values_new.xml");
				BufferedWriter out = new BufferedWriter(fstream);
				out.write(stringsFile.get(i).getTransformed());
				//Close the output stream
				out.close();
			}catch (Exception e){//Catch exception if any
				System.err.println("Error: " + e.getMessage());
			}
		}
	}

}