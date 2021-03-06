/**
 * Copyright (C) 2015-2021 Pavel Rotek (www.helger.com)
 * pavel[dot]rotek[at]gmail[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase4.profile.entsog;

import javax.annotation.Nonnull;

import com.helger.commons.annotation.IsSPIImplementation;
import com.helger.commons.functional.ISupplier;
import com.helger.phase4.model.pmode.IPModeIDProvider;
import com.helger.phase4.profile.AS4Profile;
import com.helger.phase4.profile.IAS4ProfilePModeProvider;
import com.helger.phase4.profile.IAS4ProfileRegistrar;
import com.helger.phase4.profile.IAS4ProfileRegistrarSPI;
import com.helger.phase4.profile.IAS4ProfileValidator;

/**
 * Library specific implementation of {@link IAS4ProfileRegistrarSPI}.
 *
 * @author Pavel Rotek
 */
@IsSPIImplementation
public final class AS4ENTSOGProfileRegistarSPI implements IAS4ProfileRegistrarSPI
{
		
  public static final String AS4_PROFILE_ID = "entsog";
  public static final String AS4_PROFILE_NAME = "ENTSOG";
  public static final IPModeIDProvider PMODE_ID_PROVIDER = IPModeIDProvider.DEFAULT_DYNAMIC;

  public void registerAS4Profile (@Nonnull final IAS4ProfileRegistrar aRegistrar)
  {
	final ISupplier <? extends IAS4ProfileValidator> aProfileValidatorProvider = ENTSOGCompatibilityValidator::new;
    final IAS4ProfilePModeProvider aDefaultPModeProvider = (i, r, a) -> ENTSOGPMode.createENTSOGPMode (i,
                                                                                                 r,
                                                                                                 a,
                                                                                                 PMODE_ID_PROVIDER,
                                                                                                 true);
    final AS4Profile aProfile = new AS4Profile (AS4_PROFILE_ID,
                                                AS4_PROFILE_NAME,
                                                aProfileValidatorProvider,
                                                aDefaultPModeProvider,
                                                PMODE_ID_PROVIDER,
                                                false);
    aRegistrar.registerProfile (aProfile);
  }
}
