package st.seaside;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Map;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import st.seaside.PharoBuilder.DescriptorImpl;

/**
 * {@link Builder} for <a href="http://www.pharo-project.org/">Pharo</a>
 * images.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link PharoBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(Build, Launcher, BuildListener)} method
 * will be invoked. 
 *
 * @author Philippe Marschall
 */
public class PharoBuilder extends Builder {

    private final String inputImage;
    private final String outputImage;
    private final String code;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public PharoBuilder(String inputImage, String outputImage, String code) {
        this.inputImage = inputImage;
        this.outputImage = outputImage;
        this.code = code;
    }

    public String getInputImage() {
        return this.inputImage;
    }

    public String getOutputImage() {
        return this.outputImage;
    }
    
    public String getCode() {
        return this.code;
    }
    
    private File getStartUpScript() throws IOException {
        File file = File.createTempFile("pharo_build", ".st");
        FileOutputStream stream = new FileOutputStream(file);
        try {
            OutputStreamWriter writer = new OutputStreamWriter(stream);
            writer.write(this.getCode());
            writer.close();
        } finally {
            stream.close();
        }
        return file;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getDescriptor().getVm());
        // add parameters
        String trimmed = Util.fixEmptyAndTrim(getDescriptor().getParameters());
        if (trimmed != null) {
            for (String each : trimmed.split(" ")) {
                if (each != null && !each.isEmpty()) {
                    args.add(each);
                }
            }
        }
        // add image
        args.add(getInputImage());
        
        Map<String, String> env = build.getEnvironment(listener);
        File script = this.getStartUpScript();
        try {
            args.add(script);
            listener.getLogger().println(Arrays.toString(args.toCommandArray()));
            int r = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(build.getModuleRoot()).join();
            return r == 0;
        } catch (IOException e) {
            Util.displayIOException(e,listener);

            String errorMessage = Messages.pharo_imageBuildFailed();
            e.printStackTrace(listener.fatalError(errorMessage));
            return false;
        } finally {
            //TODO uncomment
//            if (!script.delete()) {
//                listener.getLogger().println("could not delete temp file");
//            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link PharoBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/pharo-build/PharoBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // this marker indicates Hudson that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        
        private String vm;
        
        private String parameters;

//        /**
//         * Performs on-the-fly validation of the form field 'name'.
//         *
//         * @param value
//         *      This parameter receives the value that the user has typed.
//         * @return
//         *      Indicates the outcome of the validation. This is sent to the browser.
//         */
//        public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException {
//            if(value.isEmpty())
//                return FormValidation.error("Please set a name");
//            if(value.length() < 4)
//                return FormValidation.warning("Isn't the name too short?");
//            return FormValidation.ok();
//        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Pharo Image Builder";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            this.vm = formData.getString("vm");
            this.parameters = formData.getString("parameters");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        public String getVm() {
            return this.vm;
        }

        public String getParameters() {
            return this.parameters;
        }
        
        public String defaultParamters() {
            return "-nodisplay -nosound";
        }

    }
}

