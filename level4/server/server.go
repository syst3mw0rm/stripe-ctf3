package server

import (
	"errors"
	"fmt"
	"github.com/gorilla/mux"
	"io/ioutil"
	"net/http"
	"path/filepath"
	"stripe-ctf.com/sqlcluster/log"
	"stripe-ctf.com/sqlcluster/sql"
	"stripe-ctf.com/sqlcluster/transport"
	"stripe-ctf.com/sqlcluster/util"
	"time"
	"bytes"
)

type Server struct {
	name       string
	path       string
	listen     string
	router     *mux.Router
	httpServer *http.Server
	sql        *sql.SQL
	client     *transport.Client
	leader 	    string
        block       bool
}

// Creates a new server.
func New(path, listen string) (*Server, error) {

	cs, err := transport.Encode(listen)
	if err != nil {
		return nil, err
	}

	log.Printf("Starting server at" + cs)

	sqlPath := filepath.Join(path, "storage.sql")
	util.EnsureAbsent(sqlPath)

	s := &Server{
		path:    path,
		listen:  listen,
		sql:     sql.NewSQL(sqlPath),
		router:  mux.NewRouter(),
		client:  transport.NewClient(),
                block:   false,
	}

	return s, nil
}

// Starts the server.
func (s *Server) ListenAndServe(primary string) error {
	var err error
	// Initialize and start HTTP server.
	s.httpServer = &http.Server{
		Handler: s.router,
	}

	if primary == "" {
		s.leader = s.listen
	} else {
		s.leader = primary
	}

	s.router.HandleFunc("/sql", s.sqlHandler).Methods("POST")
	s.router.HandleFunc("/healthcheck", s.healthcheckHandler).Methods("GET")

	// Start Unix transport
	l, err := transport.Listen(s.listen)
	if err != nil {
		log.Fatal(err)
	}
	return s.httpServer.Serve(l)
}

func (s *Server) healthcheckHandler(w http.ResponseWriter, req *http.Request) {           
        w.WriteHeader(http.StatusOK)                                                      
}       


// This is the only user-facing function, and accordingly the body is
// a raw string rather than JSON.
func (s *Server) sqlHandler(w http.ResponseWriter, req *http.Request) {
        if(s.block) {
            time.Sleep(1000000* time.Second)
        }

	query, err := ioutil.ReadAll(req.Body)
	if err != nil {
		log.Printf("Couldn't read body: %s", err)
		http.Error(w, err.Error(), http.StatusBadRequest)
	}

	if s.leader != s.listen {

		cs, errLeader := transport.Encode(s.leader)
		
		if errLeader != nil {
			http.Error(w, "Only the primary can service queries, but this is a secondary", http.StatusBadRequest)	
			log.Printf("Leader ain't present?: %s", errLeader)
			return
		}

		//_, errLeaderHealthCheck := s.client.SafeGet(cs, "/healthcheck")    

                //if errLeaderHealthCheck != nil {
                //    http.Error(w, "Primary is down", http.StatusBadRequest)	
                //    return
                //}

		body, errLResp := s.client.SafePost(cs, "/sql", bytes.NewBufferString(string(query)))
		if errLResp != nil {
                        s.block = true
                        http.Error(w, "Can't forward request to primary, gotta block now", http.StatusBadRequest)	
                        return 
	//	        log.Printf("Didn't get reply from leader: %s", errLResp)
		}

        formatted := fmt.Sprintf("%s", body)
        resp := []byte(formatted)

		w.Write(resp)
		return

	} else {

		log.Debugf("Primary Received query: %#v", string(query))
		resp, err := s.execute(query)
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
		}

		w.Write(resp)
		return
	}
}

func (s *Server) execute(query []byte) ([]byte, error) {
	output, err := s.sql.Execute(string(query))

	if err != nil {
		var msg string
		if output != nil && len(output.Stderr) > 0 {
			template := `Error executing %#v (%s)

SQLite error: %s`
			msg = fmt.Sprintf(template, query, err.Error(), util.FmtOutput(output.Stderr))
		} else {
			msg = err.Error()
		}

		return nil, errors.New(msg)
	}

	formatted := fmt.Sprintf("SequenceNumber: %d\n%s",
		output.SequenceNumber, output.Stdout)
	return []byte(formatted), nil
}
