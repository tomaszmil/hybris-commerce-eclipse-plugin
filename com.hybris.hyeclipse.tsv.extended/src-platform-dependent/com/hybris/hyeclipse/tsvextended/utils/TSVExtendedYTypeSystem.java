package com.hybris.hyeclipse.tsvextended.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.hybris.bootstrap.typesystem.YAttributeDescriptor;
import de.hybris.bootstrap.typesystem.YComposedType;
import de.hybris.bootstrap.typesystem.YDeployment;
import de.hybris.bootstrap.typesystem.YIndex;
import de.hybris.bootstrap.typesystem.YIndexDeployment;
import de.hybris.bootstrap.typesystem.YTypeSystem;
import de.hybris.bootstrap.typesystem.YNamespace;
import de.hybris.bootstrap.typesystem.YExtension;

public class TSVExtendedYTypeSystem extends YTypeSystem {

	private static Field mergedNameSpaceField = null;
	private static Method mergeNameSpaceMethod = null;
	private static Field resolvedClassMapField = null;
	
	// horrible hack to make a private member public
	static {
		try {
		mergedNameSpaceField  = YTypeSystem.class.
		            getDeclaredField("mergedNamespace");
		mergedNameSpaceField.setAccessible(true);
		
		mergeNameSpaceMethod = YNamespace.class.getDeclaredMethod("mergeNamespace", new Class[]{YNamespace.class});
		mergeNameSpaceMethod.setAccessible(true);
		
		resolvedClassMapField = YTypeSystem.class.getDeclaredField("resolvedClassMap");
		resolvedClassMapField.setAccessible(true);
		
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to do reflection hack so we need to abort", e);
		}
	}

	public TSVExtendedYTypeSystem(boolean buildMode) {
		super(buildMode);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected void createInheritedAttributes(final YComposedType composedType, final YAttributeDescriptor inheritFrom) {
		// skip all subtypes with existing attribute
		if (getAttribute(composedType.getCode(), inheritFrom.getQualifier()) == null) {
			YAttributeDescriptor inherited = null;
			
			// fix here for bad attributes
			try {
				inherited = new YAttributeDescriptor(composedType.getCode(), inheritFrom);
			}
			catch (Exception e) {
				return;
			}
			
			inheritFrom.getNamespace().registerTypeSystemElement(inherited);
			for (final YComposedType subtype : (Set<YComposedType>) getSubtypes(composedType.getCode())) {
				createInheritedAttributes(subtype, inherited);
			}
		}
	}
	
	@Override
	protected void deployIndex(final YIndex idx) {
		try {
			final YDeployment depl = idx.getEnclosingType().getDeployment();
			final YIndexDeployment iDepl = depl.getIndexDeployment(idx.getName().toLowerCase(Locale.ENGLISH));
			if (iDepl == null) {
				final YIndexDeployment newOne = new YIndexDeployment(idx);
				idx.getNamespace().registerTypeSystemElement(newOne);
				depl.resetCaches();
			}
		}
		catch (Exception e) {
			return;
		}
		
	}
	
	protected void mergeNamespaces() {
		YNamespace namespace = new YExtension(this, "<merged>", null);
		for (final YExtension ext : getExtensions()) {
			try {
				mergeNameSpaceMethod.invoke(namespace, ext);
			} 
			catch (Exception e) {
				//LOG.error("Failed to merge namespace for extension: " + ext.getExtensionName() + ", " + e.getMessage());
			}
		}
		try {
			// set the field
			mergedNameSpaceField.set(this, namespace);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to use reflection to set namespace, aborting", e);
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected Class<?> resolveClass(final Object resolveFor, final String className)  {
		Class<?> ret = null;
		try {
			Map<String, Class> resolvedClassMapMerged = (Map<String, Class>)resolvedClassMapField.get(this);
			ret = resolvedClassMapMerged.get(className);
			
			if (ret == null && !resolvedClassMapMerged.containsKey(className)) {
				try {
					ret = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
				}
				catch (final ClassNotFoundException e) {
					if (isBuildMode()) {
						//LOG.debug("class '" + className + "' not available in build mode - ignored");
					}
					else {
						throw new IllegalStateException("invalid typesystem element " + resolveFor + " due to missing class '" + className
								+ "'");
					}
				}
				
				resolvedClassMapMerged.put(className, ret);
			}
		}
		catch (IllegalStateException ise) {
			ise.printStackTrace();
		}
		catch (IllegalAccessException iae) {
			iae.printStackTrace();
		}
		return ret;
	}
			
}