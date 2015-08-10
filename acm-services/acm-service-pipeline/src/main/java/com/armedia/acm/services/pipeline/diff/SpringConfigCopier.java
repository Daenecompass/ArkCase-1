package com.armedia.acm.services.pipeline.diff;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by armdev on 4/17/14.
 */
public class SpringConfigCopier implements ApplicationContextAware
{
    private List<String> resourcePatterns;

    private PathMatchingResourcePatternResolver resolver;

    private Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        // root custom folder, mapper to web application root
        String customRoot = "/.acm/custom";

        // built-in Spring configuration files are copied to this folder
        String builtinFolderPath = System.getProperty("user.home") + "/.acm/default-config/spring";
        File builtinFolder = new File(builtinFolderPath);

        // custom Spring configuration files are copied to this folder
        String customFolderPath = System.getProperty("user.home") + "/.acm/custom-config/spring";
        File customFolder = new File(customFolderPath);

        try
        {
            // create built-in folder if it don't exist
            if (!builtinFolder.exists())
            {
                log.info("Creating folder '{}'", builtinFolderPath);
                builtinFolder.mkdirs();
            }
            // clean-up the folder from potential residue
            FileUtils.cleanDirectory(builtinFolder);

            // create custom folder if it don't exist
            if (!customFolder.exists())
            {
                log.info("Creating folder '{}'", customFolderPath);
                customFolder.mkdirs();
            }
            // clean-up the folder from potential residue
            FileUtils.cleanDirectory(customFolder);

            // copy all the Spring configurations provided with the WAR into the built-in folder
            for (String resourcePattern : resourcePatterns)
            {
                log.info("Scanning for resources matching '{}'", resourcePattern);
                Resource[] matchingResources = getResolver().getResources(resourcePattern);
                for (Resource resource : matchingResources)
                {
                    String resourceFilename = resource.getFilename();
                    log.info("Found resource '{}'", resourceFilename);

                    if (resource.getURL().getPath().contains(customRoot))
                    {
                        // this file is a custom Spring configuration or belongs to extension module
                        File target = new File(customFolderPath + File.separator + resourceFilename);
                        log.debug("Copying resource '{}' to custom folder.", resourceFilename);
                        // NOTE: FileCopyUtils will close both the input and the output streams.
                        FileCopyUtils.copy(resource.getInputStream(), new FileOutputStream(target));

                    } else
                    {
                        // this file belongs to the WAR we are providing
                        File target = new File(builtinFolderPath + File.separator + resourceFilename);
                        log.debug("Copying resource '{}' to builtin folder.", resourceFilename);
                        // NOTE: FileCopyUtils will close both the input and the output streams.
                        FileCopyUtils.copy(resource.getInputStream(), new FileOutputStream(target));
                    }
                    log.info("Done scanning for resources matching '{}'", resourcePattern);
                }
            }
        } catch (IOException e)
        {
            log.error("Could not copy resource: {}", e.getMessage(), e);
        }
    }

    public List<String> getResourcePatterns()
    {
        return resourcePatterns;
    }

    public void setResourcePatterns(List<String> resourcePatterns)
    {
        this.resourcePatterns = resourcePatterns;
    }

    public PathMatchingResourcePatternResolver getResolver()
    {
        return resolver;
    }

    public void setResolver(PathMatchingResourcePatternResolver resolver)
    {
        this.resolver = resolver;
    }
}
