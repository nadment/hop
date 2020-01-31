/*******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.core.auth;

import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.plugins.BasePluginType;
import org.apache.hop.core.plugins.PluginAnnotationType;
import org.apache.hop.core.plugins.PluginMainClassType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.PluginTypeInterface;

import java.lang.annotation.Annotation;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Map;

/**
 * This class represents the authentication plugin type.
 */
@PluginMainClassType( AuthenticationConsumerType.class )
@PluginAnnotationType( AuthenticationConsumerPlugin.class )
public class AuthenticationConsumerPluginType extends BasePluginType implements PluginTypeInterface {
  protected static AuthenticationConsumerPluginType pluginType = new AuthenticationConsumerPluginType();

  private AuthenticationConsumerPluginType() {
    super( AuthenticationProviderPlugin.class, "AUTHENTICATION_CONSUMER", "AuthenticationConsumer" );
    populateFolders( "authentication" );
  }

  public void registerPlugin( URLClassLoader classLoader, Class<? extends AuthenticationConsumerType> clazz ) throws HopPluginException {
    AuthenticationConsumerPlugin pluginAnnotation =
      clazz.getAnnotation( AuthenticationConsumerPlugin.class );
    AuthenticationConsumerPluginType.getInstance().handlePluginAnnotation( clazz, pluginAnnotation,
      new ArrayList<>(), false, null );
    PluginRegistry.getInstance().addClassLoader( classLoader,
      PluginRegistry.getInstance().getPlugin( AuthenticationConsumerPluginType.class, pluginAnnotation.id() ) );
  }

  public static AuthenticationConsumerPluginType getInstance() {
    return pluginType;
  }

  public String[] getNaturalCategoriesOrder() {
    return new String[ 0 ];
  }

  @Override
  protected String extractCategory( Annotation annotation ) {
    return "";
  }

  @Override
  protected String extractDesc( Annotation annotation ) {
    return ( (AuthenticationConsumerPlugin) annotation ).description();
  }

  @Override
  protected String extractID( Annotation annotation ) {
    return ( (AuthenticationConsumerPlugin) annotation ).id();
  }

  @Override
  protected String extractName( Annotation annotation ) {
    return ( (AuthenticationConsumerPlugin) annotation ).name();
  }

  @Override
  protected boolean extractSeparateClassLoader( Annotation annotation ) {
    return ( (AuthenticationConsumerPlugin) annotation ).isSeparateClassLoaderNeeded();
  }

  @Override
  protected String extractI18nPackageName( Annotation annotation ) {
    return ( (AuthenticationConsumerPlugin) annotation ).i18nPackageName();
  }

  @Override
  protected void addExtraClasses( Map<Class<?>, String> classMap, Class<?> clazz, Annotation annotation ) {
  }

  @Override
  protected String extractDocumentationUrl( Annotation annotation ) {
    return ( (AuthenticationConsumerPlugin) annotation ).documentationUrl();
  }

  @Override
  protected String extractCasesUrl( Annotation annotation ) {
    return ( (AuthenticationConsumerPlugin) annotation ).casesUrl();
  }

  @Override
  protected String extractForumUrl( Annotation annotation ) {
    return ( (AuthenticationConsumerPlugin) annotation ).forumUrl();
  }

  @Override
  protected String extractSuggestion( Annotation annotation ) {
    return null;
  }

  @Override
  protected String extractImageFile( Annotation annotation ) {
    return "";
  }

  @Override
  protected void registerNatives() throws HopPluginException {

  }
}
