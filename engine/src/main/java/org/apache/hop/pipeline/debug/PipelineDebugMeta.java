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

package org.apache.hop.pipeline.debug;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopStepException;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.step.RowAdapter;
import org.apache.hop.pipeline.step.StepInterface;
import org.apache.hop.pipeline.step.StepMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * For a certain pipeline, we want to be able to insert break-points into a pipeline. These breakpoints can
 * be applied to steps. When a certain condition is met, the pipeline will be paused and the caller will be
 * informed of this fact through a listener system.
 *
 * @author Matt
 */
public class PipelineDebugMeta {

  public static final String XML_TAG = "pipeline-debug-meta";

  public static final String XML_TAG_STEP_DEBUG_METAS = "step-debug-metas";

  private PipelineMeta pipelineMeta;
  private Map<StepMeta, StepDebugMeta> stepDebugMetaMap;

  public PipelineDebugMeta( PipelineMeta pipelineMeta ) {
    this.pipelineMeta = pipelineMeta;
    stepDebugMetaMap = new HashMap<StepMeta, StepDebugMeta>();
  }

  /**
   * @return the referenced pipeline metadata
   */
  public PipelineMeta getPipelineMeta() {
    return pipelineMeta;
  }

  /**
   * @param pipelineMeta the pipeline metadata to reference
   */
  public void setPipelineMeta( PipelineMeta pipelineMeta ) {
    this.pipelineMeta = pipelineMeta;
  }

  /**
   * @return the map that contains the debugging information per step
   */
  public Map<StepMeta, StepDebugMeta> getStepDebugMetaMap() {
    return stepDebugMetaMap;
  }

  /**
   * @param stepDebugMeta the map that contains the debugging information per step
   */
  public void setStepDebugMetaMap( Map<StepMeta, StepDebugMeta> stepDebugMeta ) {
    this.stepDebugMetaMap = stepDebugMeta;
  }

  public synchronized void addRowListenersToPipeline( final IPipelineEngine<PipelineMeta> pipeline ) {

    // for every step in the map, add a row listener...
    //
    for ( final StepMeta stepMeta : stepDebugMetaMap.keySet() ) {
      final StepDebugMeta stepDebugMeta = stepDebugMetaMap.get( stepMeta );

      // What is the pipeline thread to attach a listener to?
      //
      for ( IEngineComponent component : pipeline.getComponentCopies( stepMeta.getName() ) ) {
        // TODO: Make this functionality more generic in the pipeline engines
        //
        if ( component instanceof StepInterface ) {
          StepInterface baseStep = (StepInterface) component;
          baseStep.addRowListener( new RowAdapter() {
               public void rowWrittenEvent( RowMetaInterface rowMeta, Object[] row ) throws HopStepException {
                 try {

                   // This block of code is called whenever there is a row written by the step
                   // So we want to execute the debugging actions that are specified by the step...
                   //
                   int rowCount = stepDebugMeta.getRowCount();

                   if ( stepDebugMeta.isReadingFirstRows() && rowCount > 0 ) {

                     int bufferSize = stepDebugMeta.getRowBuffer().size();
                     if ( bufferSize < rowCount ) {

                       // This is the classic preview mode.
                       // We add simply add the row to the buffer.
                       //
                       stepDebugMeta.setRowBufferMeta( rowMeta );
                       stepDebugMeta.getRowBuffer().add( rowMeta.cloneRow( row ) );
                     } else {
                       // pause the pipeline...
                       //
                       pipeline.pauseRunning();

                       // Also call the pause / break-point listeners on the step debugger...
                       //
                       stepDebugMeta.fireBreakPointListeners( PipelineDebugMeta.this );
                     }
                   } else if ( stepDebugMeta.isPausingOnBreakPoint() && stepDebugMeta.getCondition() != null ) {
                     // A break-point is set
                     // Verify the condition and pause if required
                     // Before we do that, see if a row count is set.
                     // If so, keep the last rowCount rows in memory
                     //
                     if ( rowCount > 0 ) {
                       // Keep a number of rows in memory
                       // Store them in a reverse order to keep it intuitive for the user.
                       //
                       stepDebugMeta.setRowBufferMeta( rowMeta );
                       stepDebugMeta.getRowBuffer().add( 0, rowMeta.cloneRow( row ) );

                       // Only keep a number of rows in memory
                       // If we have too many, remove the last (oldest)
                       //
                       int bufferSize = stepDebugMeta.getRowBuffer().size();
                       if ( bufferSize > rowCount ) {
                         stepDebugMeta.getRowBuffer().remove( bufferSize - 1 );
                       }
                     } else {
                       // Just keep one row...
                       //
                       if ( stepDebugMeta.getRowBuffer().isEmpty() ) {
                         stepDebugMeta.getRowBuffer().add( rowMeta.cloneRow( row ) );
                       } else {
                         stepDebugMeta.getRowBuffer().set( 0, rowMeta.cloneRow( row ) );
                       }
                     }

                     // Now evaluate the condition and see if we need to pause the pipeline
                     //
                     if ( stepDebugMeta.getCondition().evaluate( rowMeta, row ) ) {
                       // We hit the break-point: pause the pipeline
                       //
                       pipeline.pauseRunning();

                       // Also fire off the break point listeners...
                       //
                       stepDebugMeta.fireBreakPointListeners( PipelineDebugMeta.this );
                     }
                   }
                 } catch ( HopException e ) {
                   throw new HopStepException( e );
                 }
               }
             }
          );
        }
      }
    }
  }

  /**
   * Add a break point listener to all defined step debug meta data
   *
   * @param breakPointListener the break point listener to add
   */
  public void addBreakPointListers( BreakPointListener breakPointListener ) {
    for ( StepDebugMeta stepDebugMeta : stepDebugMetaMap.values() ) {
      stepDebugMeta.addBreakPointListener( breakPointListener );
    }
  }

  /**
   * @return the number of times the break-point listeners got called. This is the total for all the steps.
   */
  public int getTotalNumberOfHits() {
    int total = 0;
    for ( StepDebugMeta stepDebugMeta : stepDebugMetaMap.values() ) {
      total += stepDebugMeta.getNumberOfHits();
    }
    return total;
  }

  /**
   * @return the number of steps used to preview or debug on
   */
  public int getNrOfUsedSteps() {
    int nr = 0;

    for ( StepDebugMeta stepDebugMeta : stepDebugMetaMap.values() ) {
      if ( stepDebugMeta.isReadingFirstRows() && stepDebugMeta.getRowCount() > 0 ) {
        nr++;
      } else if ( stepDebugMeta.isPausingOnBreakPoint()
        && stepDebugMeta.getCondition() != null && !stepDebugMeta.getCondition().isEmpty() ) {
        nr++;
      }
    }

    return nr;
  }
}