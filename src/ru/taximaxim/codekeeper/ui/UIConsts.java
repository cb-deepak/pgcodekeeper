package ru.taximaxim.codekeeper.ui;

public interface UIConsts {

    String UTF_8 = "UTF-8"; //$NON-NLS-1$
    String UTC = "UTC"; //$NON-NLS-1$

    interface PLUGIN_ID {
        String THIS = "ru.taximaxim.codekeeper.ui"; //$NON-NLS-1$
        String HELP = THIS + ".help"; //$NON-NLS-1$
    }
    
    interface EDITOR {
        String PROJECT = PLUGIN_ID.THIS + ".projectEditorDiffer"; //$NON-NLS-1$
    }
    
    interface COMMAND {
        String DEPCY_SRC = PLUGIN_ID.THIS + ".toggleDepcySrc"; //$NON-NLS-1$
        String DEPCY_SRC_STATE = DEPCY_SRC + "state"; //$NON-NLS-1$
        String DEPCY_TGT = PLUGIN_ID.THIS + ".toggleDepcyTgt"; //$NON-NLS-1$
        String DEPCY_TGT_STATE = DEPCY_TGT + "state"; //$NON-NLS-1$
        /* EGit commit command id 
        (value of org.eclipse.egit.ui.internal.actions.ActionCommands.COMMIT_ACTION) */
        String COMMIT_COMMAND_ID = "org.eclipse.egit.ui.team.Commit"; //$NON-NLS-1$
    }
    
    interface PREF {
        String USE_ANTLR = "prefUseAntlr"; //$NON-NLS-1$
        String PGDUMP_EXE_PATH = "prefPgdumpExePath"; //$NON-NLS-1$
        String PGDUMP_CUSTOM_PARAMS = "prefPgdumpCustomParams"; //$NON-NLS-1$
        String FORCE_SHOW_CONSOLE = "prefForceShowConsole"; //$NON-NLS-1$
        String DB_STORE = "prefDbStore"; //$NON-NLS-1$
        String IGNORE_OBJECTS = "prefIgnoreObjects"; //$NON-NLS-1$
        
        String LAST_OPENED_LOCATION = "prefLastOpenedLocation"; //$NON-NLS-1$
        String IS_FLIPPED_DB_SOURCE = "isFlippedDbSource"; //$NON-NLS-1$
        String IS_DDL_UPDATE_OVER_JDBC = "isDdlUpdateOverJdbc"; //$NON-NLS-1$
        
        String CALL_COMMIT_COMMAND_AFTER_UPDATE = "callCommitCommandAfterUpdate";
    }
    
    interface COMMIT_PREF {
        String CONSIDER_DEPCY_IN_COMMIT = "considerDepcyInCommit"; //$NON-NLS-1$
        String USE_PARTIAL_EXPORT_ON_COMMIT = "usePartialExportOnCommit"; //$NON-NLS-1$
    }
    
    interface DB_UPDATE_PREF {
        String SHOW_SCRIPT_OUTPUT_SEPARATELY = "prefShowScriptOutputSeparately"; //$NON-NLS-1$
        String USE_PSQL_DEPCY = "prefUsePSQLDepcy"; //$NON-NLS-1$
        String DROP_TABLE_STATEMENT = "prefDropTableStatement"; //$NON-NLS-1$
        String ALTER_COLUMN_STATEMENT = "prefAlterColumnStatement"; //$NON-NLS-1$
        String DROP_COLUMN_STATEMENT = "prefDropColumnStatement"; //$NON-NLS-1$
    }
    
    public enum DBSources {
        SOURCE_TYPE_JDBC("jdbc"), //$NON-NLS-1$
        SOURCE_TYPE_DB("db"), //$NON-NLS-1$
        SOURCE_TYPE_DUMP("dump"); //$NON-NLS-1$
        
        private String sourceName;
        private DBSources(String sourceName) {
            this.sourceName = sourceName;
        }
        
        @Override
        public String toString() {
            return sourceName;
        }
        public static DBSources getEnum(String value) {
            for (DBSources v : values()) {
                if (v.sourceName.equalsIgnoreCase(value)) {
                    return v;
                }
            }
            // источником по умолчанию для всего что не попадает в enum 
            return SOURCE_TYPE_DUMP;
        }
    }
    
    interface PROJ_PREF {
        String ENCODING = "prefGeneralEncoding"; //$NON-NLS-1$
        String TIMEZONE = "prefGeneralTimezone"; //$NON-NLS-1$
        String SOURCE = "prefGeneralSource"; //$NON-NLS-1$
        String DB_NAME = "prefDbName"; //$NON-NLS-1$
        String DB_HOST = "prefDbHost"; //$NON-NLS-1$
        String DB_PORT = "prefDbPort"; //$NON-NLS-1$
        String DB_USER = "prefDbUser"; //$NON-NLS-1$
        String REPO_SUBDIR_PATH = "prefWorkingDirPath"; //$NON-NLS-1$
        String REPO_ROOT_PATH = "prefRepoPath"; //$NON-NLS-1$
    }
    
    interface NATURE {
        String ID = PLUGIN_ID.THIS + ".nature"; //$NON-NLS-1$
    }
    
    interface BUILDER {
        String ID = PLUGIN_ID.THIS + ".builder"; //$NON-NLS-1$
    }
    
    interface HELP {
        String MANUAL_DEPCIES = PLUGIN_ID.HELP + ".manual_depcies"; //$NON-NLS-1$
        String MAIN_EDITOR = PLUGIN_ID.HELP + ".pgcodekeeper_editor"; //$NON-NLS-1$
        String NEW_WIZARD = PLUGIN_ID.HELP + ".project_initializer"; //$NON-NLS-1$
        String NEW_WIZARD_INIT = PLUGIN_ID.HELP + ".schema_src_settings"; //$NON-NLS-1$
        String NEW_WIZARD_MISC = PLUGIN_ID.HELP + ".miscellaneous"; //$NON-NLS-1$
        String SQL_SCRIPT_DIALOG = PLUGIN_ID.HELP + ".roll_on_script"; //$NON-NLS-1$
    }
    
    interface FILE {
        String ICONPGADMIN = "/icons/pgadmin/"; //$NON-NLS-1$
        String ICONWARNING = "/icons/warning.gif"; //$NON-NLS-1$
        String ICONADD = "/icons/add_obj.gif"; //$NON-NLS-1$
        String ICONSAVE = "/icons/save_edit.gif"; //$NON-NLS-1$
        String ICONDEL = "/icons/delete_obj.gif"; //$NON-NLS-1$
        String ICONEDIT = "/icons/editor.gif"; //$NON-NLS-1$
        String ICONDEFAULTSORT = "/icons/alpha_mode.gif"; //$NON-NLS-1$
        String ICONSELECTALL = "/icons/check_all.gif"; //$NON-NLS-1$
        String ICONSELECTNONE = "/icons/uncheck_all.gif"; //$NON-NLS-1$
        String ICONINVERTSELECTION = "/icons/loop_obj.gif"; //$NON-NLS-1$
        String ICONBALLBLUE = "/icons/ball_blue.png"; //$NON-NLS-1$
        String ICONBALLRED = "/icons/ball_red.png"; //$NON-NLS-1$
        String ICONREFRESH = "/icons/refresh.gif"; //$NON-NLS-1$
        String ICONCLOSE = "/icons/close_view.png"; //$NON-NLS-1$
        String DDL_UPDATE_COMMANDS_HIST_FILENAME = "rollon_cmd_history.xml"; //$NON-NLS-1$
    }
    
    interface XML_TAGS{
        String DDL_UPDATE_COMMANDS_HIST_ROOT = "scripts"; //$NON-NLS-1$
        String DDL_UPDATE_COMMANDS_HIST_ELEMENT = "s"; //$NON-NLS-1$
        int DDL_UPDATE_COMMANDS_MAX_STORED = 20;
        
        String IGNORED_OBJS_ROOT = "ignored_objects"; //$NON-NLS-1$
        String IGNORED_OBJS_ELEMENT = "obj"; //$NON-NLS-1$
    }
}
