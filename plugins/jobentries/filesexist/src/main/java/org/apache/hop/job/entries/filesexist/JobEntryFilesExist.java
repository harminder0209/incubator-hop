/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2017 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.job.entries.filesexist;

import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.Const;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.JobEntry;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.vfs.HopVFS;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.job.JobMeta;
import org.apache.hop.job.entry.JobEntryBase;
import org.apache.hop.job.entry.JobEntryInterface;
import org.apache.hop.metastore.api.IMetaStore;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.List;

/**
 * This defines a Files exist job entry.
 *
 * @author Samatar
 * @since 10-12-2007
 */

@JobEntry(
  id = "FILES_EXIST",
  i18nPackageName = "org.apache.hop.job.entries.filesexist",
  name = "JobEntryFilesExist.Name",
  description = "JobEntryFilesExist.Description",
  image = "FilesExist.svg",
  categoryDescription = "i18n:org.apache.hop.job:JobCategory.Category.Conditions"
)
public class JobEntryFilesExist extends JobEntryBase implements Cloneable, JobEntryInterface {
  private static Class<?> PKG = JobEntryFilesExist.class; // for i18n purposes, needed by Translator2!!

  private String filename; // TODO: looks like it is not used: consider deleting

  private String[] arguments;

  public JobEntryFilesExist( String n ) {
    super( n, "" );
    filename = null;
  }

  public JobEntryFilesExist() {
    this( "" );
  }

  public void allocate( int nrFields ) {
    arguments = new String[ nrFields ];
  }

  public Object clone() {
    JobEntryFilesExist je = (JobEntryFilesExist) super.clone();
    if ( arguments != null ) {
      int nrFields = arguments.length;
      je.allocate( nrFields );
      System.arraycopy( arguments, 0, je.arguments, 0, nrFields );
    }
    return je;
  }

  public String getXML() {
    StringBuilder retval = new StringBuilder( 30 );

    retval.append( super.getXML() );

    retval.append( "      " ).append( XMLHandler.addTagValue( "filename", filename ) );

    retval.append( "      <fields>" ).append( Const.CR );
    if ( arguments != null ) {
      for ( int i = 0; i < arguments.length; i++ ) {
        retval.append( "        <field>" ).append( Const.CR );
        retval.append( "          " ).append( XMLHandler.addTagValue( "name", arguments[ i ] ) );
        retval.append( "        </field>" ).append( Const.CR );
      }
    }
    retval.append( "      </fields>" ).append( Const.CR );

    return retval.toString();
  }

  public void loadXML( Node entrynode,
                       IMetaStore metaStore ) throws HopXMLException {
    try {
      super.loadXML( entrynode );
      filename = XMLHandler.getTagValue( entrynode, "filename" );

      Node fields = XMLHandler.getSubNode( entrynode, "fields" );

      // How many field arguments?
      int nrFields = XMLHandler.countNodes( fields, "field" );
      allocate( nrFields );

      // Read them all...
      for ( int i = 0; i < nrFields; i++ ) {
        Node fnode = XMLHandler.getSubNodeByNr( fields, "field", i );

        arguments[ i ] = XMLHandler.getTagValue( fnode, "name" );

      }
    } catch ( HopXMLException xe ) {
      throw new HopXMLException( BaseMessages.getString(
        PKG, "JobEntryFilesExist.ERROR_0001_Cannot_Load_Job_Entry_From_Xml_Node", xe.getMessage() ) );
    }
  }

  public void setFilename( String filename ) {
    this.filename = filename;
  }

  public String getFilename() {
    return filename;
  }

  public String[] getArguments() {
    return arguments;
  }

  public void setArguments( String[] arguments ) {
    this.arguments = arguments;
  }

  public Result execute( Result previousResult, int nr ) {
    Result result = previousResult;
    result.setResult( false );
    result.setNrErrors( 0 );
    int missingfiles = 0;
    int nrErrors = 0;

    // see PDI-10270 for details
    boolean oldBehavior =
      "Y".equalsIgnoreCase( getVariable( Const.HOP_COMPATIBILITY_SET_ERROR_ON_SPECIFIC_JOB_ENTRIES, "N" ) );

    if ( arguments != null ) {
      for ( int i = 0; i < arguments.length && !parentJob.isStopped(); i++ ) {
        FileObject file = null;

        try {
          String realFilefoldername = environmentSubstitute( arguments[ i ] );
          file = HopVFS.getFileObject( realFilefoldername, this );

          if ( file.exists() && file.isReadable() ) { // TODO: is it needed to check file for readability?
            if ( log.isDetailed() ) {
              logDetailed( BaseMessages.getString( PKG, "JobEntryFilesExist.File_Exists", realFilefoldername ) );
            }
          } else {
            missingfiles++;
            if ( log.isDetailed() ) {
              logDetailed( BaseMessages.getString(
                PKG, "JobEntryFilesExist.File_Does_Not_Exist", realFilefoldername ) );
            }
          }

        } catch ( Exception e ) {
          nrErrors++;
          missingfiles++;
          logError( BaseMessages.getString( PKG, "JobEntryFilesExist.ERROR_0004_IO_Exception", e.toString() ), e );
        } finally {
          if ( file != null ) {
            try {
              file.close();
              file = null;
            } catch ( IOException ex ) { /* Ignore */
            }
          }
        }
      }

    }

    result.setNrErrors( nrErrors );

    if ( oldBehavior ) {
      result.setNrErrors( missingfiles );
    }

    if ( missingfiles == 0 ) {
      result.setResult( true );
    }

    return result;
  }

  public boolean evaluates() {
    return true;
  }

  @Override
  public void check( List<CheckResultInterface> remarks, JobMeta jobMeta, VariableSpace space,
                     IMetaStore metaStore ) {
  }

}