package ru.taximaxim.codekeeper.ui.differ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.service.prefs.BackingStoreException;

import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts.TEST;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffUtils;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement.DiffSide;
import ru.taximaxim.codekeeper.apgdiff.model.exporter.ModelExporter;
import ru.taximaxim.codekeeper.ui.PgCodekeeperUIException;
import ru.taximaxim.codekeeper.ui.UIConsts;
import ru.taximaxim.codekeeper.ui.UIConsts.PROJ_PREF;
import ru.taximaxim.codekeeper.ui.fileutils.TempDir;
import ru.taximaxim.codekeeper.ui.pgdbproject.PgDbProject;
import cz.startnet.utils.pgdiff.loader.JdbcConnector;
import cz.startnet.utils.pgdiff.loader.JdbcLoaderTest;
import cz.startnet.utils.pgdiff.loader.JdbcRunner;
import cz.startnet.utils.pgdiff.loader.ParserClass;
import cz.startnet.utils.pgdiff.loader.PgDumpLoader;
import cz.startnet.utils.pgdiff.schema.PgDatabase;

public class DbSourceTest {

    private static JdbcConnector connector;
    private static final String RESOURCE_DUMP = "remote/testing_dump.sql";
    private static final String RESOURCE_CLEANUP = "remote/testing_cleanup.sql";
    private static PgDatabase dbPredefined;
    private static File workspacePath;
    private static IWorkspaceRoot workspaceRoot;
    
    @BeforeClass
    public static void initDb() throws IOException{
        connector = new JdbcConnector(  TEST.REMOTE_HOST, 
                                        TEST.REMOTE_PORT, 
                                        TEST.REMOTE_USERNAME, 
                                        TEST.REMOTE_PASSWORD, 
                                        TEST.REMOTE_DB, 
                                        ApgdiffConsts.UTF_8, 
                                        ApgdiffConsts.UTC);
        
        // remove old schemas
        try(InputStreamReader isr = new InputStreamReader(
                JdbcLoaderTest.class.getResourceAsStream(RESOURCE_CLEANUP), "UTF-8");
                BufferedReader reader = new BufferedReader(isr)){
            
            StringBuilder script = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null) {
                script.append(line);
            }
            
            String res = new JdbcRunner(connector).runScript(script.toString());
            assertEquals("DB cleanup script returned an error: " + res, "success", res);
        }
        
        // dump schemas back
        try(InputStreamReader isr = new InputStreamReader(
                JdbcLoaderTest.class.getResourceAsStream(RESOURCE_DUMP), "UTF-8");
                BufferedReader reader = new BufferedReader(isr)){
            
            StringBuilder script = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null) {
                script.append(line);
                script.append("\n");
            }
            
            String res = new JdbcRunner(connector).runScript(script.toString());
            assertEquals("DDL update over JDBC exited with an error: " + res, "success", res);
        }
        
        dbPredefined = PgDumpLoader.loadDatabaseSchemaFromDump(
                JdbcLoaderTest.class.getResourceAsStream(RESOURCE_DUMP),
                ApgdiffConsts.UTF_8, false, false, ParserClass.getLegacy(null, 1));
        
        workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        workspacePath = workspaceRoot.getLocation().toFile();
        assertTrue("Workspace does not exist: " + workspacePath.getAbsolutePath(), workspacePath.exists());
    }
    
    @Test
    public void testJdbc() throws IOException{
        performTest(DbSource.fromJdbc(TEST.REMOTE_HOST, 
                                            TEST.REMOTE_PORT, 
                                            TEST.REMOTE_USERNAME, 
                                            TEST.REMOTE_PASSWORD, 
                                            TEST.REMOTE_DB, 
                                            UIConsts.UTF_8, 
                                            UIConsts.UTC, false));
        
    }
    
    @Test
    public void testDirTree() throws IOException{
        try(TempDir exportDir = new TempDir("pgcodekeeper-test")){
            new ModelExporter(exportDir.get(), dbPredefined, UIConsts.UTF_8).exportFull();
            
            performTest(DbSource.fromDirTree(ParserClass.getLegacy(null, 1),
                    exportDir.get().getAbsolutePath(), UIConsts.UTF_8));
        }
    }
    
    @Test
    public void testFilter () throws InvocationTargetException, PgCodekeeperUIException, IOException{
        DbSource predefined = DbSource.fromDbObject(dbPredefined, "predefined");
        final TreeDiffer differ = new TreeDiffer(predefined, 
                DbSource.fromDbObject(new PgDatabase(), "empty"));
        differ.run(new NullProgressMonitor());
        TreeElement diff = differ.getDiffTree();
        
        performTest(DbSource.fromFilter(predefined, diff, DiffSide.LEFT));
    }
    
    @Test
    public void testFile () throws IOException, URISyntaxException {
        URL urla = JdbcLoaderTest.class.getResource(RESOURCE_DUMP);
        
        performTest(DbSource.fromFile(ParserClass.getLegacy(null, 1), 
                ApgdiffUtils.getFileFromOsgiRes(urla).getCanonicalPath(), UIConsts.UTF_8));
    }
    
    @Test
    public void testProject() throws CoreException, IOException, PgCodekeeperUIException{
        try(TempDir tempDir = new TempDir(workspacePath.toPath(), "dbSourceProjectTest")){
            // create empty project in temp dir
            IProject project = createProjectInWorkspace(tempDir.get());
            
            // populate project with data
            new ModelExporter(tempDir.get(), dbPredefined, UIConsts.UTF_8).exportFull();
            
            // testing itself
            PgDbProject proj = new PgDbProject(project);
            proj.openProject();

            assertEquals("Project name differs", tempDir.get().getName(), proj.getProjectName());
            
            performTest(DbSource.fromProject(ParserClass.getLegacy(null, 1), proj));
            
            proj.deleteFromWorkspace();
        }
    }
    
    @Test
    public void testJdbcFromProject() throws CoreException, IOException, URISyntaxException, BackingStoreException, PgCodekeeperUIException{
        try(TempDir tempDir = new TempDir(workspacePath.toPath(), "dbSourceJdbcTest")){
            // create empty project in temp dir
            IProject project = createProjectInWorkspace(tempDir.get());
            
            // populate project with data
            new ModelExporter(tempDir.get(), dbPredefined, UIConsts.UTF_8).exportFull();
            
            // set required settings
            PgDbProject proj = new PgDbProject(project);
            proj.openProject();
            
            proj.getPrefs().put(PROJ_PREF.DB_NAME, TEST.REMOTE_DB);
            proj.getPrefs().put(PROJ_PREF.DB_USER, TEST.REMOTE_USERNAME);
            proj.getPrefs().put(PROJ_PREF.DB_HOST, TEST.REMOTE_HOST);
            proj.getPrefs().putInt(PROJ_PREF.DB_PORT, TEST.REMOTE_PORT);
            
            assertEquals("Project name differs", tempDir.get().getName(), proj.getProjectName());
            
            // testing itself
            performTest(DbSource.fromJdbc(proj, TEST.REMOTE_PASSWORD, false));
            
            proj.deleteFromWorkspace();
        }
    }
    
    private void performTest(DbSource source) throws IOException{
        assertFalse("DB source should not be loaded", source.isLoaded());
        
        try{
            source.getDbObject();
            fail("Source is not loaded yet, exception expected");
        }catch(IllegalStateException ex){
            // do nothing: expected behavior
        }
        PgDatabase dbSource = source.get(SubMonitor.convert(new NullProgressMonitor(), "", 1));
        
        assertTrue("DB source should be loaded", source.isLoaded());
        
        assertEquals("Db loaded not equal to predefined db", dbPredefined, dbSource);
    }
    
    private IProject createProjectInWorkspace(File projectPath) throws CoreException{
        IProject project = workspaceRoot.getProject(projectPath.getName());
        project.create(null);
        
        assertNotNull("Project location cannot be determined", project.getLocation());
        return project;
    }
}
