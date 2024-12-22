/****************************************************************************
 *
 *   Copyright (c) 2017,2020 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.mavlink.messages.MAV_SEVERITY;
import org.mavlink.messages.lquac.msg_serial_control;

import com.comino.flight.log.ProgressInputStream;
import com.comino.flight.log.ulog.UlogtoModelConverter;
import com.comino.flight.model.AnalysisDataModel;
import com.comino.flight.model.AnalysisDataModelMetaData;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.flight.observables.StateProperties;
import com.comino.flight.param.MAVGCLPX4Parameters;
import com.comino.flight.prefs.MAVPreferences;
import com.comino.mavcom.control.IMAVController;
import com.comino.mavcom.log.MSPLogger;
import com.comino.mavcom.model.DataModel;
import com.comino.mavcom.model.segment.LogMessage;
import com.comino.mavcom.param.ParameterAttributes;
import com.comino.mavcom.struct.MapPoint3D_F32;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import me.drton.jmavlib.log.FormatErrorException;
import me.drton.jmavlib.log.ulog.ULogReader;
import us.ihmc.log.LogTools;


public class FileHandler {

	private static final String BASEPATH = "/.MAVGCL";
	private static final String TMPFILE  =  "/logtmp.tmp";
	private static final int MAX_PRESETS = 15;

	private static FileHandler handler = null;

	private Stage stage;
	private String name="";
	private Preferences userPrefs;

	private UlogtoModelConverter converter = null;

	private AnalysisModelService modelService = null;
	private MAVGCLPX4Parameters  paramService = null;

	private IMAVController control;

	private Map<String,String> ulogFields = null;
	private List<String> presetfiles = null;
	private List<String> scenariofiles = null;

	private String lastDir = null;
	private String currDir = null;

	private boolean createResultSet = false;
	private DataModel currentModel = null;
	private MSPLogger logger = null;

	private DumpNutshellToFile nutshell;


	final StateProperties state = StateProperties.getInstance();


	public static FileHandler getInstance() {
		return handler;
	}

	public static FileHandler getInstance(Stage stage, IMAVController control) {
		if(handler==null)
			handler = new FileHandler(stage,control);
		return handler;
	}

	private FileHandler(Stage stage, IMAVController control) {
		super();
		this.stage = stage;
		this.presetfiles = new ArrayList<String>();
		this.scenariofiles = new ArrayList<String>();
		this.userPrefs = MAVPreferences.getInstance();
		this.control = control;
		this.modelService = AnalysisModelService.getInstance();
		this.paramService = MAVGCLPX4Parameters.getInstance();
		if(control!=null)
			this.currentModel  = control.getCurrentModel();
		this.logger = MSPLogger.getInstance();
		this.nutshell = new DumpNutshellToFile(control);

		readPresetFiles();
		readScenarioFiles();
		autoLoadKeyfigures();



	}

	private void readPresetFiles() {
		presetfiles.clear();
		File file = new File(userPrefs.get(MAVPreferences.PRESET_DIR,System.getProperty("user.home")));
		if(file.isDirectory()) {
			File[] list = file.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.contains(".mgs");
				}
			});
			Arrays.sort(list, new Comparator<File>() {
				@Override
				public int compare(File o1, File o2) {
					return (int)(o2.lastModified() - o1.lastModified());
				}
			});
			LogTools.info(list.length+" presets found");
			for(int i=0;i<list.length;i++)
				presetfiles.add(list[i].getName().substring(0, list[i].getName().length()-4));
			Collections.sort(presetfiles);
		}

	}
	
	private void readScenarioFiles() {
		scenariofiles.clear();
		File file = new File(userPrefs.get(MAVPreferences.SCENARIO_DIR,System.getProperty("user.home")));
		if(file.isDirectory()) {
			File[] list = file.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.contains(".xml");
				}
			});
			Arrays.sort(list, new Comparator<File>() {
				@Override
				public int compare(File o1, File o2) {
					return (int)(o2.lastModified() - o1.lastModified());
				}
			});
			LogTools.info(list.length+" scenarios found");
			for(int i=0;i<list.length;i++)
				scenariofiles.add(list[i].getName().substring(0, list[i].getName().length()-4));
			Collections.sort(scenariofiles);
		}

	}

	public void setCreateTestResultSet(boolean flag) {
		this.createResultSet = flag;
	}

	public String getName() {
		return name;
	}

	public String getCurrentPath() {
		return lastDir;
	}

	public void clear() {
		name = "";
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBasePath() {
		return System.getProperty("user.home")+BASEPATH;
	}

	public List<String> getPresetList() {
		return presetfiles;
	}
	
	public List<String> getScenarioList() {
		return scenariofiles;
	}

	public void fileImport() {
		final StateProperties state = StateProperties.getInstance();

		String dir = userPrefs.get(MAVPreferences.PREFS_DIR,System.getProperty("user.home"));

		if(lastDir != null)
			dir = lastDir;

		state.getReplayingProperty().set(false);

		FileChooser fileChooser = getFileDialog("Open MAVGCL model file...",dir,
				new ExtensionFilter("Log files", "*.mgc", "*.ulg", "*.px4log"));

		File file = fileChooser.showOpenDialog(stage);
		if(file!=null) {
			fileImport(file);
			addToLastFile(file.getAbsolutePath());
		} else
			state.getLogLoadedProperty().set(true);
	}


	public void fileImportLast(int index) {
		final StateProperties state = StateProperties.getInstance();
		String name = null;
		switch(index) {
		case 0:
			name = userPrefs.get(MAVPreferences.LAST_FILE,null);
			break;
		case 1:
			name = userPrefs.get(MAVPreferences.LAST_FILE2,null);
			break;
		case 2:
			name = userPrefs.get(MAVPreferences.LAST_FILE3,null);
			break;
		}
		state.getReplayingProperty().set(false);
		if(name!=null) {
			File file = new File(name);
			fileImport(file);
		}
	}

	public void fileImport(File file) {

		if(file!=null) {
			state.getLogLoadedProperty().set(false);
			lastDir = null;
			new Thread(new Task<Void>() {
				@Override protected Void call() throws Exception {

					Type listType = null;

					state.isLogLoading().set(true);

					if(file.getName().endsWith("ulg")) {
						try {
							ULogReader reader = new ULogReader(file.getAbsolutePath());
							MAVGCLPX4Parameters.getInstance().setParametersFromLog(reader.getParameters());	
							converter = new UlogtoModelConverter(reader,modelService.getModelList());	
							converter.doConversion();
							ulogFields = reader.getFieldList();
						}
						catch(FormatErrorException f) {
							logger.writeLocalMsg("[mgc] "+f.getMessage(),MAV_SEVERITY.MAV_SEVERITY_ERROR);
							state.isLogLoading().set(false);
							state.getLogLoadedProperty().set(false);
							return null;
						}
						state.isLogLoading().set(false);
					}

					if(file.getName().endsWith("mgc")) {
						listType = new TypeToken<FileData>() {}.getType();

						ProgressInputStream raw = new ProgressInputStream(new FileInputStream(file));
						raw.addListener(new ProgressInputStream.Listener() {
							@Override
							public void onProgressChanged(float percentage) {
								state.getProgressProperty().set(percentage);
							}
						});
						Reader reader = new BufferedReader(new InputStreamReader(raw));
						Gson gson = new GsonBuilder().create();
						try {
							FileData data = gson.fromJson(reader,listType);
							data.update(modelService,paramService,currentModel);
						} catch(Exception e) {
							reader.close();
							MAVGCLPX4Parameters.getInstance().clear();
							raw = new ProgressInputStream(new FileInputStream(file));
							raw.addListener(new ProgressInputStream.Listener() {
								@Override
								public void onProgressChanged(float percentage) {
									state.getProgressProperty().set(percentage);
								}
							});
							reader = new BufferedReader(new InputStreamReader(raw));
							listType = new TypeToken<ArrayList<AnalysisDataModel>>() {}.getType();
							try {
								modelService.reset();
								modelService.setModelList(gson.fromJson(reader,listType));
							} catch(Exception e1) {
								logger.writeLocalMsg("[mgc] Wrong file format",MAV_SEVERITY.MAV_SEVERITY_ERROR);
								reader.close();
								name = "";
								state.getProgressProperty().set(StateProperties.NO_PROGRESS);
								state.getLogLoadedProperty().set(false);
								state.isLogLoading().set(false);
								return null;
							}
						}
						reader.close();
						state.getProgressProperty().set(StateProperties.NO_PROGRESS);
					}

					//					if(file.getName().endsWith("px4log")) {
					//						PX4LogReader reader = new PX4LogReader(file.getAbsolutePath());
					//						MAVGCLPX4Parameters.getInstance().setParametersFromLog(reader.getParameters());
					//						PX4toModelConverter converter = new PX4toModelConverter(reader,modelService.getModelList());
					//						converter.doConversion();
					//					}
					name = file.getName();
					lastDir = file.getParentFile().getAbsolutePath();
					state.isLogLoading().set(false);
					state.getLogLoadedProperty().set(true);
					modelService.setCurrent(Integer.MAX_VALUE);
					return null;
				}
			}).start();
		} else {
			state.getLogLoadedProperty().set(false);
		}

	}


	public void fileExport() {

		FileChooser fileChooser = getFileDialog("Save to MAVGCL model file...",
				userPrefs.get(MAVPreferences.PREFS_DIR,System.getProperty("user.home")),
				new ExtensionFilter("MAVGCL model files", "*.mgc"));

		if(name.length()<2)
			name = new SimpleDateFormat("ddMMyy-HHmmss'.mgc'").format(new Date());

		fileChooser.setInitialFileName(name);
		File file = fileChooser.showSaveDialog(stage);
		if(file!=null) {
			new Thread(new Task<Void>() {
				@Override protected Void call() throws Exception {
					if(file.getName().endsWith("mgc")) {
						try {
							LogTools.info(file.getName()+" saved..");
							Writer writer = new FileWriter(file);
							FileData data = new FileData(); data.prepareData(modelService,paramService, currentModel);
							Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
							stage.getScene().setCursor(Cursor.WAIT);
							gson.toJson(data, writer);
							writer.close();
							stage.getScene().setCursor(Cursor.DEFAULT);
							StateProperties.getInstance().getLogLoadedProperty().set(true);
							name = file.getName();
						} catch(Exception e) {
							LogTools.error(e.getMessage());
							stage.getScene().setCursor(Cursor.DEFAULT);
						}
					}

					return null;

				}
			}).start();

		}
	}

	public void csvParameterImport() {

		final StateProperties state = StateProperties.getInstance();

		String dir = userPrefs.get(MAVPreferences.PREFS_DIR,System.getProperty("user.home"));
		String line = null;

		if(!state.getConnectedProperty().get()) 
			return;


		FileChooser fileChooser = getFileDialog("Open CSV parameter file...",dir,
				new ExtensionFilter("Parameter Files", "*.csv","*.txt"));

		File file = fileChooser.showOpenDialog(stage);
		if(file==null) 
			return;

		Map<String,String> params = new HashMap<String,String>();

		try {
			Reader reader = new FileReader(file);
			BufferedReader br = new BufferedReader(reader);
			while ((line = br.readLine()) != null)   {

				String[] tokens = line.toUpperCase().replaceAll("^ +| +$|( )+", "$1").split(" ",3);
				if(!tokens[0].contains("_ID")) {
					params.put(tokens[0], tokens[1]);
				}

			}
			br.close();	
		} catch (Exception e) {
			logger.writeLocalMsg("[mgc] ParameterFile could not be read.",MAV_SEVERITY.MAV_SEVERITY_ERROR);	
			return;
		}	

		MAVGCLPX4Parameters paramHandler = MAVGCLPX4Parameters.getInstance();
		if(paramHandler!=null ) {
			new Thread(new Task<Void>() {
				int count = 0; int valid = 0; 
				@Override protected Void call() throws Exception {
					params.forEach((n,v) -> {
						state.getProgressProperty().set(count++/(float)params.size());
						if(paramHandler.sendParameter(n,Float.parseFloat(v))) 
							valid++;
						else
							logger.writeLocalMsg("[mgc] "+n+" could not be set to "+v,MAV_SEVERITY.MAV_SEVERITY_WARNING);
						try { Thread.sleep(100); } catch (InterruptedException e) { }
					});
					state.getProgressProperty().set(0);
					if(count == valid)
						logger.writeLocalMsg("[mgc] Parameters set successfully",MAV_SEVERITY.MAV_SEVERITY_INFO);
					else
						logger.writeLocalMsg("[mgc] Some parameters could not be set",MAV_SEVERITY.MAV_SEVERITY_WARNING);
					return null;
				}
			}).start();
		}
	}

	public void csvExport() {

		FileChooser fileChooser = getFileDialog("Export keyfigure as csv...",
				userPrefs.get(MAVPreferences.PREFS_DIR,System.getProperty("user.home")),
				new ExtensionFilter("csv files", "*.csv"));

		AnalysisModelService service = AnalysisModelService.getInstance();
		AnalysisDataModelMetaData meta = AnalysisDataModelMetaData.getInstance();


		name = "accx.csv";
		fileChooser.setInitialFileName(name);
		File file = fileChooser.showSaveDialog(stage);
		if(file!=null) {
			new Thread(new Task<Void>() {
				@Override protected Void call() throws Exception {
					String kf = file.getName().replaceFirst("[.][^.]+$", "").toUpperCase();
					if(meta.getMetaData(kf)==null) {
						control.writeLogMessage(new LogMessage("[mgc] No export: "+kf+" not a valid keyfigure.",MAV_SEVERITY.MAV_SEVERITY_WARNING));
						return null;
					}
					double value = 0;
					if(file.getName().endsWith("csv")) {
						control.writeLogMessage(new LogMessage("[mgc] "+kf+" is exported as csv.",MAV_SEVERITY.MAV_SEVERITY_INFO));
						try {
							Writer writer = new FileWriter(file);
							for(int x=0; x<service.getModelList().size();x++) {
								value = service.getModelList().get(x).getValue(kf);
								writer.append(String.format("%#.3f; %#.7f",(x*service.getCollectorInterval_ms()/1000f),(float)value).trim());
								writer.append("\n");
							}
							writer.close();
							stage.getScene().setCursor(Cursor.DEFAULT);
							StateProperties.getInstance().getLogLoadedProperty().set(true);
							name = file.getName();
						} catch(Exception e) {
							stage.getScene().setCursor(Cursor.DEFAULT);
						}
					}

					return null;

				}
			}).start();

		}
	}

	public void autoSave() throws IOException {

		lastDir = null;

		new Thread(new Task<Void>() {
			@Override protected Void call() throws Exception {

				DataModel model = control.getCurrentModel();


				//				if(control.isSimulation())
				//					return null;

				String logname = new SimpleDateFormat("ddMMyy-HHmmss").format(new Date());
				logger.writeLocalMsg("[mgc] Saving "+logname,MAV_SEVERITY.MAV_SEVERITY_WARNING);


				String path = userPrefs.get(MAVPreferences.PREFS_DIR,System.getProperty("user.home"));
				

				if(!createResultSet) {
					lastDir = path;
					saveLog(path,logname);
				} else {

					String path_result = path+"/"+logname;
					File directory = new File(path_result);
					if(!directory.exists())
						directory.mkdir();

					lastDir = directory.getAbsolutePath();
					saveLog(path_result,logname);

					if(!control.isSimulation())
						nutshell.dump("dmesg", path_result);

					List<ParameterAttributes> params_changed = MAVGCLPX4Parameters.getInstance().getChanged();
					PrintWriter writer = new PrintWriter(path_result+"/"+logname+".txt", "UTF-8");
					writer.println("Notes for flight: "+name);
					if(model.sys.build!=null)
						writer.println("MSP Build: "+model.sys.build);
					if(model.sys.version!=null)
						writer.println("Version: "+model.sys.version);
					writer.println("==========================================================================================");
					writer.format("%-30s %-20s%-20s\n", "Changed Params","Current:","Default:");
					writer.println("==========================================================================================");
					params_changed.forEach((o) -> {
						writer.format("%-30s %-20."+o.decimals+"f%-20."+o.decimals+"f\n", o.name, o.value, o.default_val);

					});
					writer.println("==========================================================================================");
					writer.flush();
					writer.close();

					// Wait for video recording has stopped or timeout of 2 sec
					int to = 20;
					while(--to > 0 && state.getMP4RecordingProperty().get())
						Thread.sleep(100);

					File video = new File(path+"/video.mp4");
					if(video.exists()) {
						video.renameTo(new File(path_result+"/"+logname+".mp4"));
					}

				}
				name = logname+".mgc";
				state.getLogLoadedProperty().set(true);
				return null;
			}
		}).start();
	}

	public void presetsExport(Map<Integer,KeyFigurePreset> preset) {
		FileChooser fileChooser = getFileDialog("Save key figure preset to...",
				userPrefs.get(MAVPreferences.PRESET_DIR,System.getProperty("user.home")),
				new ExtensionFilter("MAVGCL preset files", "*.mgs"));
		File file = fileChooser.showSaveDialog(stage);
		if(file!=null) {
			new Thread(new Task<Void>() {
				@Override protected Void call() throws Exception {
					Writer writer = new FileWriter(file);
					stage.getScene().setCursor(Cursor.WAIT);
					Gson gson = new GsonBuilder().create();
					gson.toJson(preset, writer);
					writer.close();
					stage.getScene().setCursor(Cursor.DEFAULT);
					return null;
				}
			}).start();
		}
	}
	
	public void log_cleanup() {
		File log_dir = new File(userPrefs.get(MAVPreferences.PREFS_DIR,System.getProperty("user.home")));
		File[] logs = log_dir.listFiles((d,n) -> { return n.contains(".mgc"); });
		long time = Instant.now().toEpochMilli() - 86400_000_0;
		for(int i =0; i< logs.length;i++) {
			if(logs[i].lastModified() < time ) {
				LogTools.info("CleanUp deleted: "+logs[i].getName());
			 logs[i].delete();
			}
		}
	}

	public Map<Integer,KeyFigurePreset> presetsImport(String name) {
		File file = null;

		if(name!=null) {
			file = new File(userPrefs.get(MAVPreferences.PRESET_DIR,System.getProperty("user.home"))+"/"+name+".mgs");
		} else {
			FileChooser fileChooser = getFileDialog("Open key figure preset to...",
					userPrefs.get(MAVPreferences.PRESET_DIR,System.getProperty("user.home")),
					new ExtensionFilter("MAVGCL preset files", "*.mgs"));
			file = fileChooser.showOpenDialog(stage);
		}
		if(file!=null) {
			try {
				Type listType = new TypeToken<Map<Integer,KeyFigurePreset>>() {}.getType();
				stage.getScene().setCursor(Cursor.WAIT);
				Reader reader = new FileReader(file);
				Gson gson = new GsonBuilder().create();
				stage.getScene().setCursor(Cursor.DEFAULT);
				return gson.fromJson(reader,listType);
			} catch(Exception e) { };
		}
		return null;
	}


	public void openKeyFigureMetaDataDefinition() {
		final AnalysisDataModelMetaData meta = AnalysisDataModelMetaData.getInstance();

		FileChooser metaFile = getFileDialog("Select custom keyfigure definition file...",
				userPrefs.get(MAVPreferences.DEFINITION_DIR,System.getProperty("user.home")),
				new ExtensionFilter("Custom KeyFigure Definition File..", "*.xml"));
		File f = metaFile.showOpenDialog(stage);
		if(f!=null) {
			try {
				meta.loadModelMetaData(new FileInputStream(f), true);
				clear();
				modelService.reset();
				userPrefs.put(MAVPreferences.DEFINITION_DIR,f.getParent());
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	public void autoLoadKeyfigures() {
		final AnalysisDataModelMetaData meta = AnalysisDataModelMetaData.getInstance();
		File f = new File(userPrefs.get(MAVPreferences.DEFINITION_DIR,""));
		if(f.isDirectory()) {
			File[] paths = f.listFiles();
			for(int i=0; i<paths.length;i++) {
				try {
					if(paths[i].getName().endsWith("xml") && !paths[i].getName().startsWith("_")) {
						LogTools.info("Loading keyfigure definitions of "+paths[i].getName());
						meta.loadModelMetaData(new FileInputStream(paths[i]), true);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
	}


	public void dumpUlogFields() {
		if(ulogFields==null)
			return;
		System.out.println("=======DUMP ULOG Fields===================");
		List<String> sortedKeys=new ArrayList<String>(ulogFields.keySet());
		Collections.sort(sortedKeys);
		sortedKeys.forEach((e) -> {
			System.out.print(e);
			//						AnalysisDataModelMetaData.getInstance().getKeyFigures().forEach((k) -> {
			//							if(k.sources.get(KeyFigureMetaData.ULG_SOURCE)!=null) {
			//								if(k.sources.get(KeyFigureMetaData.ULG_SOURCE).field.equals(e)) {
			//									System.out.print("\t\t\t\t=> mapped to "+k.desc1);
			//								}
			//							}
			//						});
			System.out.println();
		});
	}


	public File getTempFile() throws IOException {
		File f = new File(getBasePath()+TMPFILE);
		if(f.exists())
			f.delete();
		f.createNewFile();
		return f;

	}

	private void addToLastFile(String name) {

		String s3 = userPrefs.get(MAVPreferences.LAST_FILE, null);
		if(s3 != null && s3.equals(name))
			return;

		String s2 = userPrefs.get(MAVPreferences.LAST_FILE2, null);
		if(s2!=null)
			userPrefs.put(MAVPreferences.LAST_FILE3,s2);

		String s1 = userPrefs.get(MAVPreferences.LAST_FILE, null);
		if(s1!=null)
			userPrefs.put(MAVPreferences.LAST_FILE2,s1);

		userPrefs.put(MAVPreferences.LAST_FILE,name);

		try {
			userPrefs.sync();
			userPrefs.flush();
		} catch (BackingStoreException e) {

		}

		LogTools.info(name);

	}

	private void saveLog(String path, String name) throws Exception {
		File f = new File(path+"/"+name+".mgc");
		if(f.exists())
			f.delete();
		f.createNewFile();
		addToLastFile(f.getAbsolutePath());
		Writer writer = new FileWriter(f);
		FileData data = new FileData(); data.prepareData(modelService,paramService, currentModel);
		Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
		gson.toJson(data, writer);
		writer.flush();
		writer.close();
	}

	private FileChooser getFileDialog(String title, String initDir, ExtensionFilter...filter) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle(title);
		fileChooser.getExtensionFilters().addAll(filter);
		File f = new File(initDir);
		if(f.exists())
			fileChooser.setInitialDirectory(f);
		else
			fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

		return fileChooser;
	}

	private class FileData {

		private Map<String,ParameterAttributes>		params = null;
		private List<AnalysisDataModel> 			data   = null;
		private Map<Integer,MapPoint3D_F32>         grid   = null;
		private int conversion_rate  = 0;

		public void prepareData(AnalysisModelService service, MAVGCLPX4Parameters param, DataModel model) {
			data   = service.getModelList();
			params = param.get();
			conversion_rate = service.getCollectorInterval_ms();
		}

		public void update(AnalysisModelService service, MAVGCLPX4Parameters param, DataModel model ) {
			if(data!=null)
				service.setModelList(data);
			if(params!=null)
				param.set(params);
			if(conversion_rate != 0)
				service.setCollectorInterval(conversion_rate * 1000);
			else
				service.setCollectorInterval(AnalysisModelService.DEFAULT_INTERVAL_US);


		}
	}

}
