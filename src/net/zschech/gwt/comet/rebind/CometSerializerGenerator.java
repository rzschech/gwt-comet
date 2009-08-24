/*
 * Copyright 2009 Richard Zschech.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.zschech.gwt.comet.rebind;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;

import net.zschech.gwt.comet.client.SerialTypes;

import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.NotFoundException;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.user.client.rpc.impl.Serializer;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import com.google.gwt.user.rebind.rpc.SerializableTypeOracle;
import com.google.gwt.user.rebind.rpc.SerializableTypeOracleBuilder;
import com.google.gwt.user.rebind.rpc.TypeSerializerCreator;

public class CometSerializerGenerator extends Generator {
	
	@Override
	public String generate(TreeLogger logger, GeneratorContext context, String typeName) throws UnableToCompleteException {
		
		TypeOracle typeOracle = context.getTypeOracle();
		
		// Create the CometSerializer impl
		String packageName = "comet";
		String className = typeName.replace('.', '_') + "Impl";
		PrintWriter printWriter = context.tryCreate(logger, packageName, className);
		
		if (printWriter != null) {
			
			try {
				JClassType type = typeOracle.getType(typeName);
				SerialTypes annotation = type.getAnnotation(SerialTypes.class);
				if (annotation == null) {
					logger.log(TreeLogger.ERROR, "No SerialTypes annotation on CometSerializer type: " + typeName);
					throw new UnableToCompleteException();
				}
				
				SerializableTypeOracleBuilder stob = new SerializableTypeOracleBuilder(logger, context.getPropertyOracle(), typeOracle);
				
				for (Class<? extends Serializable> serializable : annotation.value()) {
					stob.addRootType(logger, typeOracle.getType(serializable.getCanonicalName()));
				}
				
				// Create a resource file to receive all of the serialization information
				// computed by STOB and mark it as private so it does not end up in the
				// output.
				OutputStream pathInfo = context.tryCreateResource(logger, typeName + ".rpc.log");
				stob.setLogOutputStream(pathInfo);
				SerializableTypeOracle sto = stob.build(logger);
				if (pathInfo != null) {
					context.commitResource(logger, pathInfo).setPrivate(true);
				}
				
				// Create the serializer
				TypeSerializerCreator tsc = new TypeSerializerCreator(logger, sto, context, "comet." + typeName.replace('.', '_') + "Serializer");
				String realize = tsc.realize(logger);
				
				// Create the CometSerializer impl
				ClassSourceFileComposerFactory composerFactory = new ClassSourceFileComposerFactory(packageName, className);
				
				composerFactory.addImport(Serializer.class.getName());
				
				composerFactory.setSuperclass(typeName);
				SourceWriter sourceWriter = composerFactory.createSourceWriter(context, printWriter);
				sourceWriter.print("private Serializer SERIALIZER = new " + realize + "();");
				sourceWriter.print("protected Serializer getSerializer() {return SERIALIZER;}");
				sourceWriter.commit(logger);
			}
			catch (NotFoundException e) {
				logger.log(TreeLogger.ERROR, "", e);
				throw new UnableToCompleteException();
			}
		}
		
		return packageName + '.' + className;
	}
}
