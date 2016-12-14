package net.kodehawa.mantarobot.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

import org.reflections.Reflections;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.JDAInfo;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.kodehawa.mantarobot.cmd.management.Command;
import net.kodehawa.mantarobot.cmd.management.ICommand;
import net.kodehawa.mantarobot.cmd.management.Loader;
import net.kodehawa.mantarobot.cmd.parser.Parser;
import net.kodehawa.mantarobot.config.Config;
import net.kodehawa.mantarobot.listeners.Listener;
import net.kodehawa.mantarobot.listeners.LogListener;
import net.kodehawa.mantarobot.log.LogType;
import net.kodehawa.mantarobot.log.Logger;
import net.kodehawa.mantarobot.thread.AsyncHelper;
import net.kodehawa.mantarobot.thread.ThreadPoolHelper;
import net.kodehawa.mantarobot.util.StringArrayUtils;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class Mantaro {
	
	//Am I debugging this?
	public boolean isDebugEnabled = false;
	
	//Who is maintaining this?
	public final static String OWNER_ID = "155867458203287552";
	public final static String SERVER_MGR_ID = "155035543984537600";
	
	//Mod parameters.
	private boolean externalClassRequired = false;
	private String externalClasspath = "";
	
	//New instances.
	private static volatile Mantaro instance = new Mantaro();
	private final Parser parser = new Parser();

    //JDA and Loader. We need this and they're extremely important.
	private JDA jda;
	private Config cl;
	
	public ConcurrentHashMap<String, Command> modules = new ConcurrentHashMap<>(); //A ConcurrentHashMap of commands, with the key being the command name and the result being the Class extending Command.
	public Set<Class<? extends Command>> classes = null; //A Set of classes, which will be later on loaded on Loader.
	public Set<Class<? extends ICommand>> classes1 = null; //A Set of classes, which will be later on loaded on Loader.

	//Gets in what OS the bot is running. Useful because my machine is running Windows 10, but the server is running Linux.
	private String OS = System.getProperty("os.name").toLowerCase();
	
	private static Game game = Game.of("It's not a bug, it's a feature!");
	
	//Bot data. Will be used in About command.
	//In that command it returns it as data[0] + data[1]. Will be displayed as 1.0.0a5-2102.26112016_J3.0.BETA_95, for example. 
	//The data after the dash is the hour (4 numbers) and the date.
	public final String[] data = {"14122016", "1.0.1a1-4591"};
	
	public Mantaro()
	{
		cl = Config.load();
		this.addClasses();
	}	
	
	public static void main(String[] args){
		Logger.instance().print("MantaroBot starting...", LogType.INFO);
		String botToken = instance().getConfig().values().get("token").toString();
		instance().isDebugEnabled = (Boolean)instance().getConfig().values().get("debug");
		
		try{
			instance().jda = new JDABuilder(AccountType.BOT)
					.setToken(botToken)
					.addListener(new Listener())
					.addListener(new LogListener())
					.buildBlocking(); //For some reason buildAsync constantly disconnects me.
			instance().jda.setAutoReconnect(true);
			instance().jda.getPresence().setGame(game);
			Logger.instance().print("Started MantaroBot " + instance().data[1] + " JDA " + JDAInfo.VERSION, LogType.INFO);
		} catch(Exception e){
			e.printStackTrace();
		}
		
		new Loader();

		AsyncHelper.instance().startAsyncTask("Console outputter", 
				co ->{
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
			        PrintStream ps = new PrintStream(baos);
			        PrintStream old = System.out;
			        System.setOut(ps);
			        System.out.flush();
			        System.setOut(old);
			        Guild guild = Mantaro.instance().getSelf().getGuildsByName("Mantaro", true).get(0);
			        TextChannel consoleoutput = guild.getTextChannelsByName("console", true).get(0);
			        consoleoutput.sendMessage(baos.toString()).queue();
			        System.out.println(baos.toString());
				}
				, 1);
		
		//Random status changer.
		CopyOnWriteArrayList<String> splash = new CopyOnWriteArrayList<>();
		new StringArrayUtils("splash", splash , false);
		Runnable splashTask = () -> {
			Random r = new Random();
       	 	int i = r.nextInt(splash.size());
       	 	if(!(i == splash.size()))
       	 	{
    			instance().jda.getPresence().setGame(Game.of(splash.get(i)));
       	 	}
		};
		AsyncHelper.instance().startAsyncTask("Splash Thread", splashTask, 600);
	}
	
	//What do do when a command is called?
	public void onCommand(Parser.Container cmd) throws InstantiationException, IllegalAccessException {
		if(instance().modules.containsKey(cmd.invoke))
		{
			new Thread(() -> instance().modules.get(cmd.invoke).onCommand(cmd.args, cmd.content, cmd.event)).start();
		}
	}
	
	private void addClasses(){
		Runnable classThr = () -> {
			//Adds all the Classes extending Command to the classes HashMap. They will be later loaded in Loader.
			Reflections reflections = new Reflections("net.kodehawa.mantarobot.cmd");
			Reflections test = new Reflections("net.kodehawa.mantarobot.test.cmd");
			classes1 = test.getSubTypesOf(ICommand.class);
			classes = reflections.getSubTypesOf(Command.class);
			if(externalClassRequired){
				Reflections extReflections = new Reflections(externalClasspath);
				classes.addAll(extReflections.getSubTypesOf(Command.class));
			}
		};
		ThreadPoolHelper.instance().startThread("Load", classThr);
	}
	
	public synchronized static Mantaro instance(){
		return instance;
	}
	
	public String getMetadata(String s){
		int i = -1;
		if(s.equals("date")){ i = 0; }
		if(s.equals("build")){ i = 1; }
		
		return data[i];
	}
	
	public Parser getParser(){
		return parser;
	}
	
	public JDA getSelf(){
		return jda;
	}
	
	public boolean isWindows() {
        return (OS.contains("win"));
    }

    public boolean isUnix() {
        return (OS.contains("nix") || OS.contains("nux") || OS.contains("aix") );
    }
    
    public String getPrefix() {
        String prefix = "!-";
        return prefix;
    }
    
    public Config getConfig(){
    	return cl;
    }
    
    public void setModPath(boolean isModded, String modPackagePath){
    	this.externalClassRequired = isModded;
    	this.externalClasspath = modPackagePath;
    }
    
    protected boolean getModded(){
    	return this.externalClassRequired;
    }
    
    protected String getExternalPath(){
    	return this.externalClasspath;
    }
}