package undercast.client;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jexxus.client.ClientConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.Level;

import undercast.client.UndercastData.ServerType;
import undercast.client.UndercastData.Teams;
import undercast.client.achievements.UndercastGuiAchievement;
import undercast.client.achievements.UndercastKillsHandler;
import undercast.client.update.Undercast_UpdaterThread;
import undercast.network.client.UndercastClientConnectionListener;
import undercast.network.common.packet.VIPUser;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.relauncher.FMLRelaunchLog;

/**
 * @author Flv92
 */
@Mod(modid = UndercastModClass.MOD_NAME, name = UndercastModClass.MOD_NAME, version = UndercastModClass.MOD_VERSION)
public class UndercastModClass {

    public final static String MOD_VERSION = "1.7.1";
    public final static String MOD_NAME = "UndercastMod";
    public Integer buttonListSizeOfGuiOptions;
    protected String username = "Not_Found";
    protected Minecraft mc = Minecraft.getMinecraft();
    private boolean mainMenuActive;
    public static Configuration CONFIG;
    public static boolean brightActive;
    public float brightLevel = (float) 20.0D;
    public float defaultLevel = mc.gameSettings.gammaSetting;
    @Mod.Instance(UndercastModClass.MOD_NAME)
    private static UndercastModClass instance;
    public PlayTimeCounterThread playTimeCounter;
    public static String[] lastChatLines = new String[100];
    public UndercastChatHandler chatHandler;
    public UndercastKillsHandler achievementChatHandler;
    public UndercastGuiAchievement guiAchievement;
    public ClientConnection connection;
    public List<VIPUser> vips;
	public static int capeCounter = 0;

    /**
     * preInitialisation method automatically called by Forge with
     * 
     * @Mod.PreInit use Must be used to load config and start downloading thread if necessary.
     * @param event
     */
    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // With the renaming, the config file name changed.
        // Just renaming the old one as the new one if necessary.
        File newConfig = event.getSuggestedConfigurationFile();
        File oldConfig = new File(newConfig.getParentFile().getAbsolutePath() + "/overcastNetwork-unofficialMod.cfg");
        if (oldConfig.exists() && !newConfig.exists()) {
            oldConfig.renameTo(newConfig);
        }
        vips = new ArrayList<VIPUser>();
        chatHandler = new UndercastChatHandler();
        achievementChatHandler = new UndercastKillsHandler();
        defaultLevel = FMLClientHandler.instance().getClient().gameSettings.gammaSetting;
        CONFIG = new Configuration(newConfig);
        new UndercastConfig(CONFIG, event.getSuggestedConfigurationFile());
        new UndercastKeyHandling();
        new UndercastData();
        new Undercast_UpdaterThread();
        guiAchievement = new UndercastGuiAchievement(mc);
        connection = new ClientConnection(new UndercastClientConnectionListener(), "198.199.75.102", 15652, true);
        startCapeTimer();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        new UndercastTickHandler();
        new ChatListener();
        new UndercastConnectionHandler();
        LanguageRegistry.instance().addStringLocalization("undercast.gui", "Toggle Overcast Network mod gui");
        LanguageRegistry.instance().addStringLocalization("undercast.inGameGui", "Change server");
        LanguageRegistry.instance().addStringLocalization("undercast.fullBright", "Toggle fullbright");
        LanguageRegistry.instance().addStringLocalization("undercast.settings", "Show Undercast mod settings");
        UndercastData.filterIndex = UndercastConfig.lastUsedFilter;
    }

    /**
     * onGameTick custom method called from the tickHandler UndercastTickHandler. Only called from two kind of ticks, TickType.CLIENT and TickType.RENDER Client ticks are for remplace a gui at the exact moment where the gui appears so this is invisible Render ticks are gui ticks in order to correctly render text inside a gui
     * 
     */
    public void onGameTick(Minecraft mc) {
        
        guiAchievement.updateScreen();
        // if the game over screen is active then you have died
        // if it is the first time it is active count a death
        // if it is not don't do anything
        if (mc == null) return;
        
        if (mc.currentScreen instanceof GuiGameOver) {
            mc.currentScreen = null;
            mc.func_147108_a(new UndercastGuiGameOver());
            // if the button is enabled and the user wants to disable it
        }
        
        if (mc.currentScreen instanceof UndercastGuiGameOver && UndercastConfig.toggleTitleScreenButton) {
            ((UndercastGuiGameOver) mc.currentScreen).setTitleScreenButtonState(false);
        }

        // get debug info for the fps
        String fps = mc.debug.split(",")[0];
        int height = UndercastConfig.x;
        int width = UndercastConfig.y;
        boolean isInGameGuiEmpty = !this.mc.gameSettings.showDebugInfo && !this.mc.gameSettings.keyBindPlayerList.func_151470_d();
        // if the gui is enabled display
        // if chat is open and config says yes then show gui
        if (isInGameGuiEmpty && UndercastData.guiShowing && (mc.inGameHasFocus || UndercastConfig.showGuiChat && mc.currentScreen instanceof GuiChat)) {
            // show fps
            if (UndercastConfig.showFPS) {
                mc.fontRenderer.drawStringWithShadow(fps, width, height, 0xffff);
                height += 8;
            }
        }
        // if on OvercastNetwork server then display this info.
        // if chat is open and config says yes then show gui
        if (isInGameGuiEmpty && UndercastData.isPlayingOvercastNetwork() && UndercastData.guiShowing && (mc.inGameHasFocus || UndercastConfig.showGuiChat && mc.currentScreen instanceof GuiChat)) {
            // Server display
            if (UndercastConfig.showServer) {
                mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "S: " : "Server: ") + "\u00A76" + UndercastData.getServer(), width, height, 16777215);
                height += 8;
            }

            // Team display (based on color)
            if (UndercastConfig.showTeam && !UndercastData.isLobby) {
                mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "T: " : "Team: ") + UndercastData.getTeam(), width, height, getTeamColors());
                height += 8;
            }
            // Class display (Ghost Squadron only)
            if (UndercastConfig.showGSClass && UndercastData.currentServerType == ServerType.ghostsquadron) {
                mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "Cl: " : "Class: ") + UndercastData.currentGSClass, width, height, 2446535);
                height += 8;
            }
            // Playing Time display:
            if (UndercastConfig.showPlayingTime) {
                mc.fontRenderer.drawStringWithShadow(UndercastCustomMethods.getPlayingTimeString(), width, height, 16777215);
                height += 8;
            }
            // Match Time display:
            if (UndercastConfig.showMatchTime && !UndercastData.isLobby) {
                mc.fontRenderer.drawStringWithShadow(UndercastCustomMethods.getMatchTimeString(), width, height, 16777215);
                height += 8;
            }
            // Map fetcher:
            if (UndercastConfig.showMap && !UndercastData.isLobby) {
                if (UndercastData.getMap() != null) {
                    mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "M: " : "Current Map: ") + "\u00A7d" + UndercastData.getMap(), width, height, 16777215);
                    height += 8;
                } else {
                    UndercastData.setMap("Fetching...");
                    mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "M: " : "Current Map: ") + "\u00A78" + UndercastData.getMap(), width, height, 16777215);
                    height += 8;
                }
            }
            // Show next map
            if (UndercastConfig.showNextMap && !UndercastData.isLobby) {
                if (UndercastData.getNextMap() != null) {
                    mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "N: " : "Next Map: ") + "\u00A7d" + UndercastData.getNextMap(), width, height, 16777215);
                    height += 8;
                } else {
                    mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "N: " : "Next Map: ") + "\u00A78Loading...", width, height, 16777215);
                    height += 8;
                }
            }
            // Show KD Ratio
            if (UndercastConfig.showKD && !UndercastData.isLobby) {
                mc.fontRenderer.drawStringWithShadow(UndercastCustomMethods.getKDDisplayString(), width, height, 16777215);
                height += 8;
            }
            // show KK Ratio
            if (UndercastConfig.showKK && !UndercastData.isLobby) {
                mc.fontRenderer.drawStringWithShadow(UndercastCustomMethods.getKKDisplayString(), width, height, 16777215);
                height += 8;
            }
            // show amount of kills
            if (UndercastConfig.showKills && !UndercastData.isLobby) {
                mc.fontRenderer.drawStringWithShadow(UndercastCustomMethods.getKillDisplayString(), width, height, 16777215);
                height += 8;
            }
            // show amount of deaths
            if (UndercastConfig.showDeaths && !UndercastData.isLobby) {
                mc.fontRenderer.drawStringWithShadow(UndercastCustomMethods.getDeathDisplayString(), width, height, 16777215);
                height += 8;
            }
            // Kill Streak display
            if (UndercastConfig.showStreak && !UndercastData.isLobby) {
                mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "K: " : "Current Killstreak: ") + "\u00A75" + (int) UndercastData.getKillstreak() + "\u00A7f/\u00A75" + (int) UndercastData.getLargestKillstreak(), width, height, 16777215);
                height += 8;
            }
            // Score display
            if (UndercastConfig.showScore && !UndercastData.isLobby && UndercastData.score != 0) {
                mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "Sc: " : "Score: ") + "\u00A79" + UndercastData.score, width, height, 16777215);
                height += 8;
            }
        }

        // if you not on obs turn it off
        if ((UndercastData.team != Teams.Observers && !UndercastData.isGameOver) || !UndercastData.isPlayingOvercastNetwork()) {
            brightActive = false;
            // if full bright is on turn it off
            if (mc.gameSettings.gammaSetting >= brightLevel) {
                mc.gameSettings.gammaSetting = defaultLevel;
                if (defaultLevel >= brightLevel) {
                    mc.gameSettings.gammaSetting = (float) 0.0D;
                    defaultLevel = (float) 0.0D;
                }
            }
        }

        // gui display for obs if you have brightness
        if (isInGameGuiEmpty && UndercastData.isPlayingOvercastNetwork() && UndercastData.guiShowing && (mc.inGameHasFocus || UndercastConfig.showGuiChat && mc.currentScreen instanceof GuiChat)) {
            if (brightActive && UndercastConfig.fullBright && (UndercastData.team == Teams.Observers || UndercastData.isGameOver)) {
                mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "FB: " : "Full Bright: ") + "\u00A72ON", width, height, 16777215);
                height += 8;
            } else {
                if (!brightActive && UndercastConfig.fullBright && (UndercastData.team == Teams.Observers || UndercastData.isGameOver)) {
                    mc.fontRenderer.drawStringWithShadow((UndercastConfig.lessObstructive ? "FB: " : "Full Bright: ") + "\u00A7cOFF", width, height, 16777215);
                    height += 8;
                }
            }
        }
    }

    /**
     * Returns the team color hex based on the team you are on
     * 
     * @return hex value of team color
     */
    public int getTeamColors() {
        switch (UndercastData.getTeam()) {
            case Red:
            case Cot:
                return 0x990000;
            case Blue:
            case Bot:
                return 0x0033FF;
            case Purple:
                return 0x9933CC;
            case Cyan:
                return 0x00AAAA;
            case Yellow:
                return 0xFFFF00;
            case Lime:
            case Green:
                return 0x55FF55;
            case Orange:
                return 0xFF9900;
            case Observers:
                return 0x00FFFF;
            default:
                return 0x606060;
        }
    }
    static class CapeTimeTask extends TimerTask{

		@Override
		public void run() {
			System.out.println(UndercastModClass.capeCounter);
			UndercastModClass.capeCounter = (UndercastModClass.capeCounter + 1) % 25;
		}
    	
    }

    public static void startCapeTimer(){
    	Timer timer = new Timer();
        timer.schedule(new CapeTimeTask(), 0, 100);
    }
    /**
     * get an instance of UndercastModClass
     * 
     * @return the instance
     */
    public static UndercastModClass getInstance() {
        return instance;
    }
}
