package com.project.githubsearch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.github.kevinsawicki.http.HttpRequest;
import com.project.githubsearch.model.MavenPackage;
import com.project.githubsearch.model.Query;
import com.project.githubsearch.model.ResolvedData;
import com.project.githubsearch.model.ResolvedFile;
import com.project.githubsearch.model.Response;
import com.project.githubsearch.model.SynchronizedData;
import com.project.githubsearch.model.SynchronizedFeeder;
import com.project.githubsearch.model.SynchronizedTypeSolver;
import com.project.githubsearch.model.GithubToken;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

/**
 * Github Search Engine
 *
 */
public class AppRestAPI {

    // run multiple token
    // please make sure that the number of thread is equal with the number of tokens
    private static final int NUMBER_CORE = 1;

    // parameter for the request
    private static final String PARAM_QUERY = "q"; //$NON-NLS-1$
    private static final String PARAM_PAGE = "page"; //$NON-NLS-1$
    private static final String PARAM_PER_PAGE = "per_page"; //$NON-NLS-1$

    // links from the response header
    private static final String META_REL = "rel"; //$NON-NLS-1$
    private static final String META_NEXT = "next"; //$NON-NLS-1$
    private static final String DELIM_LINKS = ","; //$NON-NLS-1$
    private static final String DELIM_LINK_PARAM = ";"; //$NON-NLS-1$

    // response code from github
    private static final int BAD_CREDENTIAL = 401;
    private static final int RESPONSE_OK = 200;
    private static final int ABUSE_RATE_LIMITS = 403;
    private static final int UNPROCESSABLE_ENTITY = 422;
    
    // number of needed file to be resolved
    private static final int MAX_RESULT = 1;

    // folder location to save the downloaded files and jars
    private static String DATA_LOCATION = "src/main/java/com/project/githubsearch/data/new/";
    private static final String JARS_LOCATION = "src/main/java/com/project/githubsearch/jars/";

    private static final String endpoint = "https://api.github.com/search/code";

    private static SynchronizedData synchronizedData = new SynchronizedData();
    private static SynchronizedFeeder synchronizedFeeder = new SynchronizedFeeder();
    private static ResolvedData resolvedData = new ResolvedData();
    private static SynchronizedTypeSolver synchronizedTypeSolver = new SynchronizedTypeSolver();

    private static Instant start;
    private static Instant currentTime;

    private static String countFile = 0;


    public static void main(String[] args) {
       
        String input = "";

        //String host = "api.github.com";
        String host = "https://api.github.com"; //"gitlab.com"; //"api.stripe.com";

        //String host = "api.stripe.com";

        //"apimanagement.azure.com/2018-01-01";

        //["delete /subscriptions/{}/resourceGroups/{}/providers/Microsoft.ApiManagement/service/{}/policies/{}",
        // "get /subscriptions/{}/resourceGroups/{}/providers/Microsoft.ApiManagement/service/{}/policies", 
        // "get /subscriptions/{}/resourceGroups/{}/providers/Microsoft.ApiManagement/service/{}/policies/{}", "get /subscriptions/{}/resourceGroups/{}/providers/Microsoft.ApiManagement/service/{}/policySnippets", "get /subscriptions/{}/resourceGroups/{}/providers/Microsoft.ApiManagement/service/{}/regions", "head /subscriptions/{}/resourceGroups/{}/providers/Microsoft.ApiManagement/service/{}/policies/{}", "put /subscriptions/{}/resourceGroups/{}/providers/Microsoft.ApiManagement/service/{}/policies/{}"]
        
       
        
        //"googleapis.com/drive/v2/";	
        
        //String[] inputs = {"/geographies/{geo-id}/media/recent/get", "/media/popular/get" (974), "/users/self/feed/get"};

        //String[] inputs =  {"/oauth2/v1/userinfo/get", "/oauth2/v1/tokeninfo/post"};

        //String[] inputs =  {"/oauth2/v1/tokeninfo/post"};

        String[] inputs_github =  {
        // "/teams/{teamId}/members/{username}/put", //0
        // "/teams/{teamId}/members/{username}/get", //found
        // "/legacy/issues/search/{owner}/{repository}/{state}/{keyword}/get", //1
        // "/legacy/user/email/{email}/get", //0
        // "/repos/{owner}/{repo}/downloads/{downloadId}/get",//0
        "/user/subscriptions/{owner}/{repo}/get", 
        "/legacy/user/search/{keyword}/get",  //check
        "/repos/{owner}/{repo}/downloads/get", 
        "/repos/{owner}/{repo}/downloads/{downloadId}/delete",
        "/teams/{teamId}/members/{username}/delete", 
        "/user/subscriptions/{owner}/{repo}/delete",
        "/user/subscriptions/{owner}/{repo}/put", 
        "/legacy/repos/search/{keyword}/get"   //found
        };

        String[] inputs_stripe = {
            "/v1/customers/{customer}/cards/get", "/v1/bitcoin/receivers/{id}/get", "/v1/customers/{customer}/cards/{id}/get",
            "/v1/recipients/get", "/v1/bitcoin/receivers/{receiver}/transactions/get", "/v1/bitcoin/transactions/get", 
            "/v1/customers/{customer}/bank_accounts/get", "/v1/recipients/post", "/v1/customers/{customer}/bank_accounts/{id}/get", 
            "/v1/recipients/{id}/post", "/v1/bitcoin/receivers/get", "/v1/issuer_fraud_records/get", "/v1/recipients/{id}/get", 
            "/v1/recipients/{id}/delete", "/v1/issuer_fraud_records/{issuer_fraud_record}/get"
        };
        
        String[] inputs_gitlab = {"/v3/licenses/{name}/get", "/v3/projects/{id}/merge_requests/{merge_request_id}/comments/get", "/v3/dockerfiles/get", 
        "/v3/gitlab_ci_ymls/get", "/v3/projects/{id}/merge_requests/{merge_request_id}/comments/post", 
        "/v3/projects/{id}/merge_request/{merge_request_id}/get", "/v3/gitignores/{name}/get", "/v3/dockerfiles/{name}/get", 
        "/v3/gitlab_ci_ymls/{name}/get", "/v3/gitignores/get", "/v3/licenses/get", 
        "/v3/projects/{id}/merge_request/{merge_request_id}/comments/get", "/v3/projects/{id}/merge_request/{merge_request_id}/comments/post"};

        String[] inp = {"/apis/apps/v1beta1/watch/namespaces/{namespace}/statefulsets/{name}_get", "/api/v1/watch/namespaces/{namespace}/pods_get", "/apis/policy/v1beta1/watch/namespaces/{namespace}/poddisruptionbudgets/{name}_get", "/apis/storage.k8s.io/v1beta1/watch/csinodes/{name}_get", "/apis/extensions/v1beta1/watch/namespaces/{namespace}/deployments/{name}_get", "/apis/certificates.k8s.io/v1beta1/watch/certificatesigningrequests/{name}_get", "/apis/batch/v1/watch/jobs_get", "/api/v1/watch/namespaces/{namespace}/events/{name}_get", "/apis/networking.k8s.io/v1beta1/watch/namespaces/{namespace}/ingresses/{name}_get", "/apis/extensions/v1beta1/watch/ingresses_get", "/apis/rbac.authorization.k8s.io/v1/watch/clusterrolebindings_get", "/apis/apps/v1beta2/watch/daemonsets_get", "/api/v1/watch/namespaces/{namespace}/services_get", "/apis/apiextensions.k8s.io/v1/watch/customresourcedefinitions_get", "/apis/autoscaling/v2beta2/watch/horizontalpodautoscalers_get", "/apis/coordination.k8s.io/v1/watch/namespaces/{namespace}/leases/{name}_get", "/apis/coordination.k8s.io/v1/watch/namespaces/{namespace}/leases_get", "/apis/admissionregistration.k8s.io/v1beta1/watch/validatingwebhookconfigurations_get", "/apis/storage.k8s.io/v1beta1/watch/csidrivers/{name}_get", "/apis/apps/v1/watch/namespaces/{namespace}/replicasets/{name}_get", "/apis/apps/v1beta1/watch/namespaces/{namespace}/controllerrevisions_get", "/apis/storage.k8s.io/v1/watch/volumeattachments_get", "/api/v1/watch/persistentvolumes/{name}_get", "/apis/apiextensions.k8s.io/v1/watch/customresourcedefinitions/{name}_get", "/apis/autoscaling/v1/watch/namespaces/{namespace}/horizontalpodautoscalers/{name}_get", "/apis/admissionregistration.k8s.io/v1beta1/watch/mutatingwebhookconfigurations_get", "/api/v1/watch/namespaces/{namespace}/services/{name}_get", "/apis/apps/v1beta1/watch/deployments_get", "/apis/batch/v2alpha1/watch/namespaces/{namespace}/cronjobs_get", "/apis/extensions/v1beta1/watch/namespaces/{namespace}/deployments_get", "/apis/rbac.authorization.k8s.io/v1alpha1/watch/namespaces/{namespace}/rolebindings/{name}_get", "/apis/storage.k8s.io/v1beta1/watch/storageclasses_get", "/apis/node.k8s.io/v1beta1/watch/runtimeclasses/{name}_get", "/apis/batch/v1beta1/watch/namespaces/{namespace}/cronjobs/{name}_get", "/apis/autoscaling/v2beta1/watch/namespaces/{namespace}/horizontalpodautoscalers_get", "/apis/storage.k8s.io/v1/watch/storageclasses/{name}_get", "/api/v1/watch/configmaps_get", "/apis/apiregistration.k8s.io/v1beta1/watch/apiservices/{name}_get", "/apis/apps/v1/watch/namespaces/{namespace}/controllerrevisions_get", "/apis/scheduling.k8s.io/v1/watch/priorityclasses_get", "/apis/rbac.authorization.k8s.io/v1beta1/watch/namespaces/{namespace}/rolebindings_get", "/api/v1/watch/namespaces/{namespace}/configmaps/{name}_get", "/api/v1/watch/serviceaccounts_get", "/apis/scheduling.k8s.io/v1/watch/priorityclasses/{name}_get", "/apis/apps/v1/watch/namespaces/{namespace}/controllerrevisions/{name}_get", "/apis/storage.k8s.io/v1/watch/volumeattachments/{name}_get", "/apis/rbac.authorization.k8s.io/v1beta1/watch/clusterrolebindings/{name}_get", "/api/v1/watch/pods_get", "/apis/events.k8s.io/v1beta1/watch/events_get", "/api/v1/watch/namespaces/{namespace}/resourcequotas/{name}_get", "/api/v1/watch/limitranges_get", "/api/v1/watch/namespaces/{namespace}/resourcequotas_get", "/apis/rbac.authorization.k8s.io/v1alpha1/watch/clusterrolebindings/{name}_get", "/apis/apiregistration.k8s.io/v1/watch/apiservices/{name}_get", "/apis/extensions/v1beta1/watch/namespaces/{namespace}/ingresses/{name}_get", "/apis/apps/v1beta1/watch/namespaces/{namespace}/statefulsets_get", "/apis/networking.k8s.io/v1/watch/networkpolicies_get", "/api/v1/watch/namespaces/{namespace}/secrets/{name}_get", "/apis/events.k8s.io/v1beta1/watch/namespaces/{namespace}/events_get", "/apis/certificates.k8s.io/v1beta1/watch/certificatesigningrequests_get", "/apis/storage.k8s.io/v1beta1/watch/storageclasses/{name}_get", "/apis/batch/v1beta1/watch/cronjobs_get", "/apis/discovery.k8s.io/v1alpha1/watch/endpointslices_get", "/apis/scheduling.k8s.io/v1alpha1/watch/priorityclasses/{name}_get", "/api/v1/watch/services_get", "/apis/rbac.authorization.k8s.io/v1alpha1/watch/namespaces/{namespace}/roles_get", "/apis/admissionregistration.k8s.io/v1beta1/watch/validatingwebhookconfigurations/{name}_get", "/api/v1/watch/namespaces/{name}_get", "/apis/apiextensions.k8s.io/v1beta1/watch/customresourcedefinitions_get", "/apis/rbac.authorization.k8s.io/v1beta1/watch/roles_get", "/apis/storage.k8s.io/v1alpha1/watch/volumeattachments/{name}_get", "/apis/extensions/v1beta1/watch/networkpolicies_get", "/apis/admissionregistration.k8s.io/v1/watch/mutatingwebhookconfigurations/{name}_get", "/apis/rbac.authorization.k8s.io/v1alpha1/watch/clusterroles/{name}_get", "/apis/rbac.authorization.k8s.io/v1beta1/watch/rolebindings_get", "/apis/extensions/v1beta1/watch/replicasets_get", "/apis/extensions/v1beta1/watch/podsecuritypolicies_get", "/apis/rbac.authorization.k8s.io/v1beta1/watch/namespaces/{namespace}/roles/{name}_get", "/api/v1/watch/namespaces/{namespace}/configmaps_get", "/api/v1/watch/replicationcontrollers_get", "/apis/apps/v1beta1/watch/statefulsets_get", "/apis/admissionregistration.k8s.io/v1/watch/validatingwebhookconfigurations/{name}_get", "/apis/policy/v1beta1/watch/namespaces/{namespace}/poddisruptionbudgets_get", "/apis/apps/v1beta2/watch/namespaces/{namespace}/statefulsets_get", "/apis/apps/v1/watch/namespaces/{namespace}/deployments/{name}_get", "/api/v1/watch/namespaces/{namespace}/endpoints/{name}_get", "/apis/apps/v1beta2/watch/namespaces/{namespace}/controllerrevisions_get", "/apis/extensions/v1beta1/watch/namespaces/{namespace}/daemonsets/{name}_get", "/apis/policy/v1beta1/watch/poddisruptionbudgets_get", "/apis/rbac.authorization.k8s.io/v1/watch/roles_get", "/api/v1/watch/resourcequotas_get", "/api/v1/watch/nodes/{name}_get", "/apis/policy/v1beta1/watch/podsecuritypolicies_get", "/apis/apps/v1/watch/namespaces/{namespace}/daemonsets_get", "/api/v1/watch/secrets_get", "/apis/rbac.authorization.k8s.io/v1alpha1/watch/clusterroles_get", "/apis/apps/v1/watch/controllerrevisions_get", "/apis/autoscaling/v2beta2/watch/namespaces/{namespace}/horizontalpodautoscalers/{name}_get", "/apis/extensions/v1beta1/watch/namespaces/{namespace}/networkpolicies/{name}_get", "/apis/apps/v1beta2/watch/namespaces/{namespace}/replicasets/{name}_get", "/apis/apps/v1beta2/watch/namespaces/{namespace}/daemonsets_get", "/apis/autoscaling/v1/watch/horizontalpodautoscalers_get", "/api/v1/watch/namespaces/{namespace}/limitranges/{name}_get", "/apis/apps/v1beta1/watch/namespaces/{namespace}/deployments/{name}_get", "/apis/scheduling.k8s.io/v1beta1/watch/priorityclasses_get", "/api/v1/watch/endpoints_get", "/apis/rbac.authorization.k8s.io/v1beta1/watch/clusterroles/{name}_get", "/apis/storage.k8s.io/v1beta1/watch/volumeattachments_get", "/apis/rbac.authorization.k8s.io/v1alpha1/watch/rolebindings_get", "/apis/settings.k8s.io/v1alpha1/watch/podpresets_get", "/apis/admissionregistration.k8s.io/v1/watch/validatingwebhookconfigurations_get", "/apis/node.k8s.io/v1beta1/watch/runtimeclasses_get", "/apis/node.k8s.io/v1alpha1/watch/runtimeclasses_get", "/api/v1/watch/namespaces/{namespace}/persistentvolumeclaims_get", "/apis/discovery.k8s.io/v1alpha1/watch/namespaces/{namespace}/endpointslices_get", "/apis/rbac.authorization.k8s.io/v1/watch/namespaces/{namespace}/roles_get", "/apis/extensions/v1beta1/watch/daemonsets_get", "/apis/coordination.k8s.io/v1beta1/watch/namespaces/{namespace}/leases/{name}_get", "/apis/storage.k8s.io/v1alpha1/watch/volumeattachments_get", "/apis/batch/v1/watch/namespaces/{namespace}/jobs_get", "/apis/scheduling.k8s.io/v1beta1/watch/priorityclasses/{name}_get", "/apis/admissionregistration.k8s.io/v1/watch/mutatingwebhookconfigurations_get", "/apis/autoscaling/v2beta1/watch/namespaces/{namespace}/horizontalpodautoscalers/{name}_get", "/apis/apps/v1/watch/namespaces/{namespace}/statefulsets/{name}_get", "/apis/apiextensions.k8s.io/v1beta1/watch/customresourcedefinitions/{name}_get", "/apis/extensions/v1beta1/watch/namespaces/{namespace}/replicasets/{name}_get", "/apis/apps/v1beta2/watch/namespaces/{namespace}/deployments/{name}_get", "/apis/apps/v1beta2/watch/statefulsets_get", "/apis/batch/v1/watch/namespaces/{namespace}/jobs/{name}_get", "/api/v1/watch/namespaces/{namespace}/persistentvolumeclaims/{name}_get", "/apis/events.k8s.io/v1beta1/watch/namespaces/{namespace}/events/{name}_get", "/apis/rbac.authorization.k8s.io/v1alpha1/watch/roles_get", "/apis/autoscaling/v2beta1/watch/horizontalpodautoscalers_get", "/apis/rbac.authorization.k8s.io/v1beta1/watch/clusterroles_get", "/apis/apps/v1beta1/watch/namespaces/{namespace}/controllerrevisions/{name}_get", "/apis/apps/v1beta2/watch/namespaces/{namespace}/statefulsets/{name}_get", "/apis/coordination.k8s.io/v1beta1/watch/leases_get", "/apis/settings.k8s.io/v1alpha1/watch/namespaces/{namespace}/podpresets_get", "/apis/batch/v2alpha1/watch/cronjobs_get", "/api/v1/watch/namespaces/{namespace}/limitranges_get", "/api/v1/watch/namespaces/{namespace}/events_get", "/apis/storage.k8s.io/v1beta1/watch/csinodes_get", "/apis/coordination.k8s.io/v1/watch/leases_get", "/api/v1/watch/podtemplates_get", "/api/v1/watch/namespaces/{namespace}/endpoints_get", "/apis/batch/v1beta1/watch/namespaces/{namespace}/cronjobs_get", "/apis/apiregistration.k8s.io/v1/watch/apiservices_get", "/apis/apps/v1/watch/namespaces/{namespace}/statefulsets_get", "/apis/apps/v1/watch/replicasets_get", "/apis/extensions/v1beta1/watch/namespaces/{namespace}/replicasets_get", "/api/v1/watch/namespaces/{namespace}/replicationcontrollers_get", "/apis/rbac.authorization.k8s.io/v1beta1/watch/clusterrolebindings_get", "/apis/storage.k8s.io/v1/watch/storageclasses_get", "/apis/extensions/v1beta1/watch/namespaces/{namespace}/daemonsets_get", "/api/v1/watch/namespaces/{namespace}/serviceaccounts_get",
         "/apis/autoscaling/v1/watch/namespaces/{namespace}/horizontalpodautoscalers_get", "/api/v1/watch/nodes_get", 
         "/api/v1/watch/persistentvolumeclaims_get", "/api/v1/watch/namespaces/{namespace}/serviceaccounts/{name}_get", 
         "/apis/extensions/v1beta1/watch/namespaces/{namespace}/ingresses_get", 
         "/apis/networking.k8s.io/v1/watch/namespaces/{namespace}/networkpolicies_get", 
         "/apis/apps/v1/watch/daemonsets_get", "/apis/apps/v1beta2/watch/controllerrevisions_get", 
         "/apis/coordination.k8s.io/v1beta1/watch/namespaces/{namespace}/leases_get", 
         "/apis/discovery.k8s.io/v1alpha1/watch/namespaces/{namespace}/endpointslices/{name}_get", 
         "/apis/autoscaling/v2beta2/watch/namespaces/{namespace}/horizontalpodautoscalers_get", 
         "/apis/rbac.authorization.k8s.io/v1/watch/clusterroles_get", "/apis/extensions/v1beta1/watch/deployments_get", 
         "/apis/apps/v1/watch/namespaces/{namespace}/replicasets_get", 
         "/apis/rbac.authorization.k8s.io/v1/watch/namespaces/{namespace}/roles/{name}_get", 
         "/apis/networking.k8s.io/v1/watch/namespaces/{namespace}/networkpolicies/{name}_get", 
         "/apis/rbac.authorization.k8s.io/v1beta1/watch/namespaces/{namespace}/rolebindings/{name}_get", 
         "/api/v1/watch/namespaces/{namespace}/pods/{name}_get", "/apis/networking.k8s.io/v1beta1/watch/namespaces/{namespace}/ingresses_get", 
         "/apis/rbac.authorization.k8s.io/v1alpha1/watch/clusterrolebindings_get", 
         "/apis/settings.k8s.io/v1alpha1/watch/namespaces/{namespace}/podpresets/{name}_get", 
         "/apis/rbac.authorization.k8s.io/v1beta1/watch/namespaces/{namespace}/roles_get", 
         "/apis/apps/v1beta2/watch/namespaces/{namespace}/daemonsets/{name}_get", 
         "/apis/extensions/v1beta1/watch/namespaces/{namespace}/networkpolicies_get", 
         "/apis/rbac.authorization.k8s.io/v1alpha1/watch/namespaces/{namespace}/rolebindings_get", "/apis/auditregistration.k8s.io/v1alpha1/watch/auditsinks/{name}_get", "/apis/apps/v1beta1/watch/namespaces/{namespace}/deployments_get", "/apis/apps/v1beta2/watch/namespaces/{namespace}/controllerrevisions/{name}_get", "/api/v1/watch/persistentvolumes_get", "/apis/apps/v1/watch/namespaces/{namespace}/daemonsets/{name}_get", "/apis/policy/v1beta1/watch/podsecuritypolicies/{name}_get", "/api/v1/watch/namespaces/{namespace}/secrets_get", "/apis/rbac.authorization.k8s.io/v1/watch/clusterrolebindings/{name}_get", "/apis/apps/v1/watch/statefulsets_get", "/apis/apps/v1beta1/watch/controllerrevisions_get", "/apis/rbac.authorization.k8s.io/v1/watch/rolebindings_get", "/api/v1/watch/namespaces/{namespace}/replicationcontrollers/{name}_get", "/apis/apps/v1beta2/watch/namespaces/{namespace}/deployments_get", "/apis/storage.k8s.io/v1beta1/watch/csidrivers_get", "/apis/apps/v1beta2/watch/replicasets_get", "/apis/rbac.authorization.k8s.io/v1/watch/clusterroles/{name}_get", "/apis/node.k8s.io/v1alpha1/watch/runtimeclasses/{name}_get", "/apis/admissionregistration.k8s.io/v1beta1/watch/mutatingwebhookconfigurations/{name}_get", "/apis/networking.k8s.io/v1beta1/watch/ingresses_get", "/apis/rbac.authorization.k8s.io/v1alpha1/watch/namespaces/{namespace}/roles/{name}_get", "/api/v1/watch/namespaces/{namespace}/podtemplates_get", "/apis/rbac.authorization.k8s.io/v1/watch/namespaces/{namespace}/rolebindings/{name}_get", "/api/v1/watch/namespaces_get", "/apis/extensions/v1beta1/watch/podsecuritypolicies/{name}_get", "/apis/rbac.authorization.k8s.io/v1/watch/namespaces/{namespace}/rolebindings_get", "/apis/apps/v1/watch/namespaces/{namespace}/deployments_get", "/apis/scheduling.k8s.io/v1alpha1/watch/priorityclasses_get", "/apis/auditregistration.k8s.io/v1alpha1/watch/auditsinks_get", "/api/v1/watch/events_get", "/apis/apps/v1beta2/watch/namespaces/{namespace}/replicasets_get", "/apis/apps/v1/watch/deployments_get", "/apis/apiregistration.k8s.io/v1beta1/watch/apiservices_get", "/apis/storage.k8s.io/v1beta1/watch/volumeattachments/{name}_get", "/apis/batch/v2alpha1/watch/namespaces/{namespace}/cronjobs/{name}_get", "/api/v1/watch/namespaces/{namespace}/podtemplates/{name}_get", "/apis/apps/v1beta2/watch/deployments_get"};
        //{"/subscriptions/{}/resourceGroups/{}/providers/Microsoft.ApiManagement/service/{}/policies/get"};

        //"apps/{}/get", "files/{}/children/{}/get", "files/{}/parents/get", "files/{}/properties/post", 				
        
        //String[] inputs = {"files/{}/children/{}/delete", "files/{}/parents/post"};

        // String[] inputs = {"files/{}/children/{}/delete", "files/{}/parents/{}/delete", "files/{}/properties/{}/delete", "apps/get",  "changes/{}/get", 
        // "files/{}/children/get", "files/{}/parents/{}/get", "files/{}/properties/get", "files/{}/properties/{}/get",
        // "files/{}/realtime/get", "permissionIds/{}/get", "files/{}/properties/{}/patch", "files/{}/children/post", "files/{}/parents/post", 
        // "files/{}/touch/post", "files/{}/trash/post", "files/{}/untrash/post", "drives/{}/put", "files/{}/put", "files/{}/comments/{}/put", "files/{}/comments/{}/replies/{}/put",
        // "files/{}/permissions/{}/put", "files/{}/properties/{}/put", "files/{}/realtime/put", "files/{}/revisions/{}/put", "teamdrives/{}/put"};

        String[] inputs = {

            "/orgs/{org}/projects/get",
            "/teams/{team_id}/get",
            "/repos/{owner}/{repo}/readme/get",
            "/repos/{owner}/{repo}/pulls/get",
            "/orgs/{org}/blocks/{username}/get",
            "/user/blocks/get",
            "/repos/{owner}/{repo}/collaborators/get",
            "/repos/{owner}/{repo}/git/ref/{ref}/get",
            "/repos/{owner}/{repo}/actions/artifacts/{artifact_id}/get",
            "/teams/{team_id}/memberships/{username}/get"


            // "/calendars/{calendarId}/events/{eventId}/get",
            // "/calendars/{calendarId}/events/get",
            // "/users/me/calendarList/{calendarId}/get"


            // "/users/lookup.json/get",
            // "/users/suggestions/{slug}/members.json/get",
            // "/blocks/create.json/post",
            // "/favorites/list.json/get",
            // "/users/show.json/get",
            // "/friends/ids.json/get",
            // "/lists/show.json/get",
            // "/blocks/ids.json/get",
            // "/friendships/update.json/post",
            // "/favorites/create.json/post",
            // "/users/suggestions/{slug}.json/get",
            // "/help/privacy.json/get",
            // "/blocks/list.json/get",
            // "/lists/destroy.json/post",
            // "/statuses/show/{id}.json/get",
            // "/friendships/create.json/post",
            // "/users/suggestions.json/get",
            // "/lists/update.json/post",
            // "/direct_messages/new.json/post",
            // "/lists/members/create.json/post",
            // "/friendships/show.json/get"            
        
        };

        int countf = 0;
        for (String s: inputs) {
            System.out.println(host + s);
            countf = countf + 1;
            // if (countf == 30) {
            //     break;
            // }
            start = Instant.now();
            if (s != null) {
                DATA_LOCATION = "src/main/java/com/project/githubsearch/data/complex5/";
                String folderName = "github/op" + Integer.toString(countf);
                initUniqueFolderToSaveData(folderName);
                // //System.out.println(folderName);
                processQuery(host+s);
            }
        }
    }

    private static void processQuery(String query) {
        int lower_bound, upper_bound, page, per_page_limit;
        lower_bound = 0;
        upper_bound = 384000;
        page = 1;
        per_page_limit = 100;

        Response response = handleCustomGithubRequest(query, lower_bound, upper_bound, page, per_page_limit);
        String nextUrlRequest;
        if (response.getTotalCount() == 0) {
            System.out.println("No item match with the query");
        } else {

            System.out.println("Total Count");

            System.out.println(response.getTotalCount());
            JSONArray item = response.getItem();

            String htmlUrl = "";

            int id = 0;
            Queue<String> data = new LinkedList<>();
            for (int it = 0; it < item.length(); it++) {
                JSONObject instance = new JSONObject(item.get(it).toString());
                id = id + 1;
                htmlUrl = instance.getString("html_url");
                //System.out.println("ID: " + id);
                //System.out.println("File Url: " + htmlUrl);
                downloadAndResolveFile(id, htmlUrl, query);
            }
            // while (id < response.getTotalCount()) {
            //     nextUrlRequest = response.getNextUrlRequest();   

            //     if (nextUrlRequest != null) {
            //         response = handleGithubRequestWithUrl(nextUrlRequest);

            //         if (response != null) {
            //             item = response.getItem();
            //             for (int ite = 0; ite < item.length(); ite++) {
            //                 JSONObject instance = new JSONObject(item.get(ite).toString());
            //                 id = id + 1;
            //                 htmlUrl = instance.getString("html_url");
            //                 //System.out.println();
            //                 //System.out.println("ID: " + id);
            //                 //System.out.println("File Url: " + htmlUrl);
            //                 downloadAndResolveFile(id, htmlUrl, query);
            //             }
            //         }
            //     }
                
            // }
        }

    }

    public static void downloadAndResolveFile (int id, String htmlUrl, String query) {
        boolean isDownloaded = downloadFile(htmlUrl, id);
    }

    
    private static boolean downloadFile(String htmlUrl, int fileId){
        // convert html url to downloadable url
        // based on my own analysis
        String downloadableUrl = convertHTMLUrlToDownloadUrl(htmlUrl);

        // using it to make a unique name
        // replace java to txt for excluding from maven builder
        // String fileName = fileId + ".txt";

        String fileName = fileId + ".js";


        // System.out.println();
        // System.out.println("Downloading the file: " + (fileId));
        // System.out.println("HTML Url: " + htmlUrl);

        boolean finished = false;

        try {
            // download file from url
            URL url;
            url = new URL(downloadableUrl);
            ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
            String pathFile = new String(DATA_LOCATION + "files/" + fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(pathFile);
            fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            fileOutputStream.close();
            finished = true;
        } catch (FileNotFoundException e) {
            System.out.println("Can't download the github file");
            System.out.println("File not found!");
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL Exception while downloading!");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Can't save the downloaded file");
        }

        return finished;
    }

    private static void initUniqueFolderToSaveData(String folderName) {
        File dataFolder = new File(DATA_LOCATION);
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }

        DATA_LOCATION = DATA_LOCATION + folderName + "/";

        File exactFolder = new File(DATA_LOCATION);
        if (!exactFolder.exists()) {
            exactFolder.mkdir();
        }

        File files = new File(DATA_LOCATION + "files/");
        if (!files.exists()) {
            files.mkdir();
        }

        // File jarFolder = new File(JARS_LOCATION);
        // if (!jarFolder.exists()) {
        //     jarFolder.mkdir();
        // }

    }

    /**
     * Convert github html url to download url input:
     * https://github.com/shuchen007/ThinkInJavaMaven/blob/85b402a81fc0b3f2f039b34c23be416322ef14b4/src/main/java/sikaiClient/IScyxKtService.java
     * output:
     * https://raw.githubusercontent.com/shuchen007/ThinkInJavaMaven/85b402a81fc0b3f2f039b34c23be416322ef14b4/src/main/java/sikaiClient/IScyxKtService.java
     */
    private static String convertHTMLUrlToDownloadUrl(String html_url) {
        String[] parts = html_url.split("/");
        String download_url = "https://raw.githubusercontent.com/";
        int l = parts.length;
        for (int i = 0; i < l; i++) {
            if (i >= 3) {
                if (i != 5) {
                    if (i != l - 1) {
                        download_url = download_url.concat(parts[i] + '/');
                    } else {
                        download_url = download_url.concat(parts[i]);
                    }
                }
            }
        }

        return download_url;
    }

    private static Response handleGithubRequestWithUrl(String url) {

        boolean response_ok = false;
        Response response = new Response();
        int responseCode;

        // encode the space into %20
        //url = url.replace(" ", "%20");
        GithubToken token = synchronizedFeeder.getAvailableGithubToken();

        do {
            HttpRequest request = HttpRequest.get(url, false).authorization("token " + token.getToken());
            System.out.println();
            //System.out.println("Request: " + request);
            // System.out.println("Token: " + token);
            // System.out.println("Thread: " + Thread.currentThread().toString());

            // handle response
            responseCode = request.code();
            if (responseCode == RESPONSE_OK) {
                // System.out.println("Header: " + request.headers());
                response.setCode(responseCode);
                JSONObject body = new JSONObject(request.body());
                response.setTotalCount(body.getInt("total_count"));
                if (body.getInt("total_count") > 0) {
                    response.setItem(body.getJSONArray("items"));
                    response.setUrlRequest(request.toString());
                    response.setNextUrlRequest(getNextLinkFromResponse(request.header("Link")));
                }
                response_ok = true;
            } else if (responseCode == BAD_CREDENTIAL) {
                System.out.println("Authorization problem");
                System.out.println("Please read the readme file!");
                System.out.println("Github message: " + (new JSONObject(request.body())).getString("message"));
                System.exit(-1);
            } else if (responseCode == ABUSE_RATE_LIMITS) {
                System.out.println("Abuse Rate Limits");
                // retry current progress after wait for a minute
                String retryAfter = request.header("Retry-After");
                try {
                    int sleepTime = 0; // wait for a while
                    if (retryAfter == null || retryAfter.isEmpty()) {
                        sleepTime = 30;
                    } else {
                        sleepTime = new Integer(retryAfter).intValue();
                    }
                    //System.out.println("Retry-After: " + sleepTime);
                    TimeUnit.SECONDS.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (responseCode == UNPROCESSABLE_ENTITY) {
                System.out.println("Response Code: " + responseCode);
                System.out.println("Unprocessable Entity: only the first 1000 search results are available");
                System.out.println("See the documentation here: https://developer.github.com/v3/search/");
            } else {
                System.out.println("Response Code: " + responseCode);
                System.out.println("Response Body: " + request.body());
                System.out.println("Response Headers: " + request.headers());
                System.exit(-1);
            }

        } while (!response_ok && responseCode != UNPROCESSABLE_ENTITY);

        synchronizedFeeder.releaseToken(token);

        return response;
    }

    private static Response handleCustomGithubRequest(String query, int lower_bound, int upper_bound, int page,
            int per_page_limit) {
        // The size range is exclusive
        upper_bound++;
        lower_bound--;
        String size = lower_bound + ".." + upper_bound; // lower_bound < size < upper_bound

        String search_query = "";

        String[] words = query.split("/");

        Integer ln = 0;

        if (words.length > 20) {
            ln = 10;
        } else {
            ln = words.length;
        }

        for (int i = 0; i < ln; i++) {
            String val = words[i];
            String part = val.trim();

            if (part.length() == 1) {
                System.out.println(part);  //@todo: what is the value? any special character
            }
            
            if (!part.contains("{") & !part.contains("[") & !part.contains("]") & part.length() > 1 & !part.contains("#")){
                // System.out.println("val");

                // System.out.println(part.length());
                search_query = search_query + part + "%20";
            }
        }

        String url;
        Response response = new Response();

        url = endpoint + "?" + PARAM_QUERY + "=" + "$.ajax%20" + search_query + "+extension:js" + "&" + PARAM_PAGE + "=" + page + "&" + PARAM_PER_PAGE + "=" + per_page_limit ;
        
        //url = endpoint + "?" + PARAM_QUERY + "=" + search_query + "+extension:js" + "&" + PARAM_PAGE + "=" + page + "&" + PARAM_PER_PAGE + "=" + per_page_limit ;

        
        System.out.print(url);

        response = handleGithubRequestWithUrl(url);

        return response;
    }

    private static String getNextLinkFromResponse(String linkHeader) {

        String next = null;

        if (linkHeader != null) {
            String[] links = linkHeader.split(DELIM_LINKS);
            for (String link : links) {
                String[] segments = link.split(DELIM_LINK_PARAM);
                if (segments.length < 2)
                    continue;

                String linkPart = segments[0].trim();
                if (!linkPart.startsWith("<") || !linkPart.endsWith(">")) //$NON-NLS-1$ //$NON-NLS-2$
                    continue;
                linkPart = linkPart.substring(1, linkPart.length() - 1);

                for (int i = 1; i < segments.length; i++) {
                    String[] rel = segments[i].trim().split("="); //$NON-NLS-1$
                    if (rel.length < 2 || !META_REL.equals(rel[0]))
                        continue;

                    String relValue = rel[1];
                    if (relValue.startsWith("\"") && relValue.endsWith("\"")) //$NON-NLS-1$ //$NON-NLS-2$
                        relValue = relValue.substring(1, relValue.length() - 1);

                    if (META_NEXT.equals(relValue))
                        next = linkPart;
                }
            }
        }
        return next;
    }

}
