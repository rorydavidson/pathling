/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationDefinition;

/**
 * This class is used for serving up the OperationDefinition resources which describe this server's
 * FHIR API.
 *
 * @author John Grimes
 */
public class OperationDefinitionProvider implements IResourceProvider {

  private final AnalyticsServerConfiguration configuration;
  private final IParser jsonParser;
  private Map<String, String> idToResourcePath;

  private static final Map<String, OperationDefinition> resourceCache = new HashMap<>();

  public OperationDefinitionProvider(AnalyticsServerConfiguration configuration,
      FhirContext fhirContext) {
    this.configuration = configuration;
    jsonParser = fhirContext.newJsonParser();

    idToResourcePath = new HashMap<String, String>() {{
      put("OperationDefinition/aggregate-" + configuration.getMajorVersion(),
          "fhir/aggregate.OperationDefinition.json");
      put("OperationDefinition/import-" + configuration.getMajorVersion(),
          "fhir/import.OperationDefinition.json");
      put("OperationDefinition/search-" + configuration.getMajorVersion(),
          "fhir/search.OperationDefinition.json");
    }};
  }

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return OperationDefinition.class;
  }

  @Read()
  public OperationDefinition getOperationDefinitionById(@IdParam IdType id) {
    return getResource(id.getValueAsString());
  }

  private OperationDefinition getResource(String id) {
    OperationDefinition cached = resourceCache.get(id);
    if (cached != null) {
      return cached;
    }
    String resourcePath = idToResourcePath.get(id);
    if (resourcePath == null) {
      throw new ResourceNotFoundException("OperationDefinition not found: " + id);
    }
    InputStream resourceStream = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(resourcePath);
    OperationDefinition operationDefinition = (OperationDefinition) jsonParser
        .parseResource(resourceStream);
    operationDefinition.setVersion(configuration.getVersion());
    return operationDefinition;
  }

}
