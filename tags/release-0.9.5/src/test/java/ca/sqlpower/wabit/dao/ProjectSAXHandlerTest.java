/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit.dao;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import ca.sqlpower.sql.DataSourceCollection;
import ca.sqlpower.sql.PlDotIni;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sqlobject.SQLDatabase;
import ca.sqlpower.util.DefaultUserPrompter;
import ca.sqlpower.util.UserPrompter;
import ca.sqlpower.util.UserPrompter.UserPromptResponse;
import ca.sqlpower.util.UserPrompterFactory.UserPromptType;
import ca.sqlpower.wabit.StubWabitSession;
import ca.sqlpower.wabit.StubWabitSessionContext;
import ca.sqlpower.wabit.WabitProject;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.WabitSessionContext;
import ca.sqlpower.wabit.query.QueryCache;
import junit.framework.TestCase;

public class ProjectSAXHandlerTest extends TestCase {

	/**
	 * This is a fake database to be used in testing.
	 */
	private SQLDatabase db;
	private PlDotIni plIni;
	
	@Override
	protected void setUp() throws Exception {
		plIni = new PlDotIni();
        plIni.read(new File("src/test/java/pl.regression.ini"));
        SPDataSource ds = plIni.getDataSource("regression_test");

        db = new SQLDatabase(ds);
	}
	
	/**
	 * Tests loading a project with a data source that no longer exists in
	 * the list of data sources can be replaced by a new data source.
	 */
	public void testMissingDSIsReplaced() throws Exception {
		SPDataSource newDS = new SPDataSource(db.getDataSource());
		newDS.setName("Missing DS is replaced");
		WabitProject p = new WabitProject();
		p.setName("Project");
		p.addDataSource(newDS);

		QueryCache query = new QueryCache();
		p.addQuery(query);
		query.setDataSource(newDS);
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ProjectXMLDAO saveDAO = new ProjectXMLDAO(out, p);
		saveDAO.save();
		System.out.println(out.toString("utf-8"));
        
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        
        final SPDataSource replacementDS = new SPDataSource(plIni); 
        replacementDS.setName("Replacement DS");
        WabitSessionContext context = new StubWabitSessionContext() {
            @Override
            public DataSourceCollection getDataSources() {
                return plIni;
            }
        	
        	@Override
        	public WabitSession createSession() {
        		return new StubWabitSession(this) {
        			public UserPrompter createUserPrompter(String question, String okText, String newText, String notOkText, String cancelText, ca.sqlpower.util.UserPrompterFactory.UserPromptType responseType, UserPrompter.UserPromptResponse defaultResponseType, Object defaultResponse) {
        				if (responseType == UserPromptType.DATA_SOURCE) {
        					return new DefaultUserPrompter(UserPromptResponse.NEW, replacementDS);
        				} else {
        					return super.createUserPrompter(question, okText, newText, notOkText, cancelText, responseType, defaultResponseType, defaultResponse);
        				}
        			};
        		};
        	}
        };
        
        LoadProjectXMLDAO loadDAO = new LoadProjectXMLDAO(context, in);
	
        WabitSession loadedSession = loadDAO.loadProjects().get(0);
        assertEquals(1, loadedSession.getProject().getQueries().size());
        QueryCache loadedQuery = (QueryCache) loadedSession.getProject().getQueries().get(0);
        System.out.println(loadedQuery.getWabitDataSource().getName());
        assertEquals(replacementDS, loadedQuery.getDataSource());
	}
}