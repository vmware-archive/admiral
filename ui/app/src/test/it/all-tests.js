import 'common/helpers/configure';
import 'common/helpers/utils';

var testsContext = require.context('.', true, /IT\.js$/);
testsContext.keys().forEach(testsContext);
