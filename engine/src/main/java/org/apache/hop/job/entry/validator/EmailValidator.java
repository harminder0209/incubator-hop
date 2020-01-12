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

package org.apache.hop.job.entry.validator;

import org.apache.commons.validator.GenericValidator;
import org.apache.commons.validator.util.ValidatorUtils;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.CheckResultSourceInterface;

import java.util.List;

public class EmailValidator implements JobEntryValidator {

  public static final EmailValidator INSTANCE = new EmailValidator();

  private static final String VALIDATOR_NAME = "email";

  public String getName() {
    return VALIDATOR_NAME;
  }

  public boolean validate( CheckResultSourceInterface source, String propertyName,
                           List<CheckResultInterface> remarks, ValidatorContext context ) {
    String value = null;

    value = ValidatorUtils.getValueAsString( source, propertyName );

    if ( !GenericValidator.isBlankOrNull( value ) && !GenericValidator.isEmail( value ) ) {
      JobEntryValidatorUtils.addFailureRemark(
        source, propertyName, VALIDATOR_NAME, remarks, JobEntryValidatorUtils.getLevelOnFail(
          context, VALIDATOR_NAME ) );
      return false;
    } else {
      return true;
    }
  }

}