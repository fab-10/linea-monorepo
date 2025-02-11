package wizardutils

import (
	"fmt"
	"strings"

	"github.com/consensys/linea-monorepo/prover/protocol/coin"
	"github.com/consensys/linea-monorepo/prover/protocol/ifaces"
	"github.com/consensys/linea-monorepo/prover/protocol/variables"
	"github.com/consensys/linea-monorepo/prover/symbolic"
	"github.com/consensys/linea-monorepo/prover/utils"
)

// Parse the metadata of an expression and returns the first round where this function can be evaluated
func LastRoundToEval(expr *symbolic.Expression) int {
	board := expr.Board()
	metadatas := board.ListVariableMetadata()

	maxRound := 0

	for _, m := range metadatas {
		switch metadata := m.(type) {
		case ifaces.Column:
			maxRound = utils.Max(maxRound, metadata.Round())
		// The expression can involve random coins
		case coin.Info:
			maxRound = utils.Max(maxRound, metadata.Round)
			// assert the coin is an expression
			if metadata.Type != coin.Field {
				utils.Panic("The coin %v should be of type `Field`", metadata.Name)
			}
		case variables.X, variables.PeriodicSample:
			// Do nothing
		case ifaces.Accessor:
			maxRound = utils.Max(maxRound, metadata.Round())
		default:
			panic("unreachable")
		}
	}

	return maxRound
}

// ExprIsOnSameLengthHandles checks that all the variables of the expression
// that are [ifaces.Column] have the same size (and panics if it does not), then
// returns the match.
func ExprIsOnSameLengthHandles(board *symbolic.ExpressionBoard) int {

	var (
		metadatas = board.ListVariableMetadata()
		length    = 0
	)

	for _, m := range metadatas {
		switch metadata := m.(type) {
		case ifaces.Column:
			// Initialize the length with the first commitment
			if length == 0 {
				length = metadata.Size()
			}

			// Sanity-check the vector should all have the same length
			if length != metadata.Size() {
				utils.Panic("Inconsistent length for %v (has size %v, but expected %v)", metadata.GetColID(), metadata.Size(), length)
			}
		// The expression can involve random coins
		case coin.Info, variables.X, variables.PeriodicSample, ifaces.Accessor:
			// Do nothing
		default:
			utils.Panic("unknown type %T", metadata)
		}
	}

	// No commitment were found in the metadata, thus this call is broken
	if length == 0 {
		utils.Panic("declared a handle from an expression which does not contains any handle")
	}

	return length
}

// maximal round of declaration for a list of commitment
func MaxRound(handles ...ifaces.Column) int {
	res := 0
	for _, handle := range handles {
		res = utils.Max(res, handle.Round())
	}
	return res
}

// DeriveName is used to construct either [ifaces.QueryID] or [ifaces.ColID] or
// [coin.Name]. The function will format [ifaces.Query], [ifaces.Column] or
// [coin.Info] using their names or IDs, in the other cases it will use the
// default `%v` formatting. The final returned name is obtained by stitching
// all the formatted inputs with an underscore.
func DeriveName[R ~string](ss ...any) R {

	fmtted := []string{}

	for i := range ss {
		switch s := ss[i].(type) {
		case ifaces.Column:
			fmtted = append(fmtted, string(s.GetColID()))
		case ifaces.Query:
			fmtted = append(fmtted, string(s.Name()))
		case coin.Info:
			fmtted = append(fmtted, string(s.Name))
		default:
			st := fmt.Sprintf("%v", s)
			fmtted = append(fmtted, st)
		}
	}

	return R(strings.Join(fmtted, "_"))
}

// AsExpr convert x into an expression expecting x to be either an expression and
// a column. The function also ensure that the expression can safely be
// interpreted as a column.
func AsExpr(x any) (e *symbolic.Expression, round, size int) {

	switch c1 := x.(type) {
	case ifaces.Column:
		round = c1.Round()
		size = c1.Size()
		e = symbolic.NewVariable(c1)
		return e, round, size
	case *symbolic.Expression:
		board := c1.Board()
		e = c1
		size = ExprIsOnSameLengthHandles(&board)
		round = LastRoundToEval(c1)
		return e, round, size
	}

	panic("unexpected type")
}
